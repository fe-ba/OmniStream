package co.omnistream.domain.service.ingestion;

import co.omnistream.domain.exception.FileRejected;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.MediaFile;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.policy.TextFilePolicy;
import co.omnistream.domain.port.out.IFileStorage;
import co.omnistream.domain.port.out.IMetadataExtractor;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Concrete handler of the ingestion chain: extracts file data and metadata (Chain of
 * Responsibility).
 */
public final class ExtractionStep extends IngestionStep {

  private static final Map<String, MediaType> EXTENSIONS =
      Map.ofEntries(
          Map.entry("mp4", MediaType.VIDEO),
          Map.entry("mkv", MediaType.VIDEO),
          Map.entry("webm", MediaType.VIDEO),
          Map.entry("avi", MediaType.VIDEO),
          Map.entry("mov", MediaType.VIDEO),
          Map.entry("m4v", MediaType.VIDEO),
          Map.entry("wmv", MediaType.VIDEO),
          Map.entry("flv", MediaType.VIDEO),
          Map.entry("mp3", MediaType.AUDIO),
          Map.entry("flac", MediaType.AUDIO),
          Map.entry("wav", MediaType.AUDIO),
          Map.entry("ogg", MediaType.AUDIO),
          Map.entry("aac", MediaType.AUDIO),
          Map.entry("m4a", MediaType.AUDIO),
          Map.entry("opus", MediaType.AUDIO),
          Map.entry("wma", MediaType.AUDIO),
          Map.entry("jpg", MediaType.IMAGE),
          Map.entry("jpeg", MediaType.IMAGE),
          Map.entry("png", MediaType.IMAGE),
          Map.entry("webp", MediaType.IMAGE),
          Map.entry("gif", MediaType.IMAGE),
          Map.entry("bmp", MediaType.IMAGE),
          Map.entry("tiff", MediaType.IMAGE),
          Map.entry("tif", MediaType.IMAGE),
          Map.entry("heic", MediaType.IMAGE),
          Map.entry("svg", MediaType.IMAGE),
          Map.entry("pdf", MediaType.DOCUMENT),
          Map.entry("txt", MediaType.DOCUMENT),
          Map.entry("md", MediaType.DOCUMENT),
          Map.entry("epub", MediaType.DOCUMENT),
          Map.entry("doc", MediaType.DOCUMENT),
          Map.entry("docx", MediaType.DOCUMENT),
          Map.entry("odt", MediaType.DOCUMENT),
          Map.entry("csv", MediaType.DOCUMENT),
          Map.entry("json", MediaType.DOCUMENT),
          Map.entry("xml", MediaType.DOCUMENT),
          Map.entry("yaml", MediaType.DOCUMENT),
          Map.entry("yml", MediaType.DOCUMENT));

  private final IFileStorage storage;
  private final IMetadataExtractor extractor;
  private final Clock clock;

  public ExtractionStep(IFileStorage storage, IMetadataExtractor extractor, Clock clock) {
    this.storage = Objects.requireNonNull(storage, "storage");
    this.extractor = Objects.requireNonNull(extractor, "extractor");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  protected boolean process(IngestionContext ctx) {
    Objects.requireNonNull(ctx, "ctx");
    Path path = Objects.requireNonNull(ctx.path, "ctx.path");
    if (!storage.exists(path)) {
      throw new ResourceNotFound("File not found: " + path);
    }
    String fileName = extractFileName(path);
    MediaType type = inferMediaType(fileName);
    Fingerprint fingerprint = storage.computeFingerprint(path);
    List<MetadataEntry> metadata =
        extractor.supports(type) ? List.copyOf(extractor.extract(path)) : List.of();
    long sizeBytes = resolveSizeBytes(path, metadata);
    Duration duration = resolveDuration(metadata);

    MediaFile file =
        MediaFile.create(fileName, path, fingerprint, type, sizeBytes, duration, clock.instant());
    metadata.forEach(file::addMetadata);
    ctx.file = file;
    return true;
  }

  private static String extractFileName(Path path) {
    Path fileName = path.getFileName();
    if (fileName == null || fileName.toString().isBlank()) {
      throw new FileRejected("Path has no file name: " + path);
    }
    return fileName.toString();
  }

  private static MediaType inferMediaType(String fileName) {
    int dot = fileName.lastIndexOf('.');
    if (dot <= 0 || dot == fileName.length() - 1) {
      throw new FileRejected("File has no recognized extension: " + fileName);
    }
    String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    MediaType type = EXTENSIONS.get(extension);
    if (type == null && TextFilePolicy.EXTENSIONS.contains(extension)) {
      // Every file the text editor opens can be uploaded: source code, patches, notes (ADR 0027).
      type = MediaType.DOCUMENT;
    }
    if (type == null) {
      throw new FileRejected("Unsupported file extension: ." + extension);
    }
    return type;
  }

  private long resolveSizeBytes(Path path, List<MetadataEntry> metadata) {
    Optional<Long> fromMetadata =
        findMetadata(metadata, "sizeBytes")
            .or(() -> findMetadata(metadata, "size"))
            .flatMap(ExtractionStep::parseNonNegativeLong);
    if (fromMetadata.isPresent()) {
      return fromMetadata.get();
    }
    try (InputStream stream = storage.read(path)) {
      byte[] buffer = new byte[8192];
      long total = 0;
      int read;
      while ((read = stream.read(buffer)) != -1) {
        total += read;
      }
      return total;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read file size for " + path, e);
    }
  }

  private static Duration resolveDuration(List<MetadataEntry> metadata) {
    return findMetadata(metadata, "duration")
        .flatMap(ExtractionStep::parseDuration)
        .orElse(Duration.ZERO);
  }

  private static Optional<String> findMetadata(List<MetadataEntry> metadata, String key) {
    return metadata.stream()
        .filter(entry -> entry.key().equals(key))
        .map(MetadataEntry::value)
        .findFirst();
  }

  private static Optional<Long> parseNonNegativeLong(String raw) {
    try {
      long parsed = Long.parseLong(raw.strip());
      return parsed >= 0 ? Optional.of(parsed) : Optional.empty();
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
  }

  private static Optional<Duration> parseDuration(String raw) {
    String value = raw.strip();
    if (value.isEmpty()) {
      return Optional.empty();
    }
    if (value.startsWith("P") || value.startsWith("p")) {
      try {
        Duration parsed = Duration.parse(value);
        return parsed.isNegative() ? Optional.empty() : Optional.of(parsed);
      } catch (DateTimeParseException ignored) {
        return Optional.empty();
      }
    }
    try {
      double seconds = Double.parseDouble(value);
      if (!Double.isFinite(seconds) || seconds < 0) {
        return Optional.empty();
      }
      return Optional.of(Duration.ofNanos(Math.round(seconds * 1_000_000_000.0)));
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
  }
}
