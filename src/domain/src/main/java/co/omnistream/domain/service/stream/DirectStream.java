package co.omnistream.domain.service.stream;

import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.StreamProfile;
import co.omnistream.domain.port.out.IFileStorage;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Concrete strategy: plays the file as it is (Strategy). */
public final class DirectStream implements StreamStrategy {

  private static final Map<String, String> MIME_BY_EXTENSION =
      Map.ofEntries(
          Map.entry("mp4", "video/mp4"),
          Map.entry("mkv", "video/x-matroska"),
          Map.entry("webm", "video/webm"),
          Map.entry("avi", "video/x-msvideo"),
          Map.entry("mov", "video/quicktime"),
          Map.entry("m4v", "video/x-m4v"),
          Map.entry("wmv", "video/x-ms-wmv"),
          Map.entry("flv", "video/x-flv"),
          Map.entry("mp3", "audio/mpeg"),
          Map.entry("flac", "audio/flac"),
          Map.entry("wav", "audio/wav"),
          Map.entry("ogg", "audio/ogg"),
          Map.entry("aac", "audio/aac"),
          Map.entry("m4a", "audio/mp4"),
          Map.entry("opus", "audio/opus"),
          Map.entry("wma", "audio/x-ms-wma"),
          Map.entry("jpg", "image/jpeg"),
          Map.entry("jpeg", "image/jpeg"),
          Map.entry("png", "image/png"),
          Map.entry("webp", "image/webp"),
          Map.entry("gif", "image/gif"),
          Map.entry("bmp", "image/bmp"),
          Map.entry("tiff", "image/tiff"),
          Map.entry("tif", "image/tiff"),
          Map.entry("heic", "image/heic"),
          Map.entry("svg", "image/svg+xml"),
          Map.entry("pdf", "application/pdf"),
          Map.entry("txt", "text/plain"),
          Map.entry("md", "text/markdown"),
          Map.entry("epub", "application/epub+zip"),
          Map.entry("doc", "application/msword"),
          Map.entry(
              "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
          Map.entry("odt", "application/vnd.oasis.opendocument.text"),
          Map.entry("csv", "text/csv"),
          Map.entry("json", "application/json"),
          Map.entry("xml", "application/xml"),
          Map.entry("yaml", "application/yaml"),
          Map.entry("yml", "application/yaml"));

  private final IFileStorage storage;

  public DirectStream(IFileStorage storage) {
    this.storage = Objects.requireNonNull(storage, "storage");
  }

  @Override
  public boolean supports(MediaFileSnapshot a, StreamProfile p) {
    Objects.requireNonNull(a, "file");
    Objects.requireNonNull(p, "profile");
    return p.isDirectPlay();
  }

  @Override
  public MediaStream generateStream(MediaFileSnapshot a, StreamProfile p, Duration pos) {
    Objects.requireNonNull(a, "file");
    Objects.requireNonNull(p, "profile");
    Objects.requireNonNull(pos, "position");
    if (!storage.exists(a.path())) {
      throw new ResourceNotFound("File not found in storage: " + a.path());
    }
    InputStream data = storage.read(a.path());
    String mime = resolveMime(a);
    return new MediaStream(data, mime, false, Optional.of(a.sizeBytes()));
  }

  private static String resolveMime(MediaFileSnapshot file) {
    return extensionOf(file.path().getFileName())
        .or(() -> extensionOf(file.name()))
        .map(MIME_BY_EXTENSION::get)
        .orElseGet(() -> fallbackMime(file.type()));
  }

  private static Optional<String> extensionOf(Path fileName) {
    return fileName == null ? Optional.empty() : extensionOf(fileName.toString());
  }

  private static Optional<String> extensionOf(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return Optional.empty();
    }
    int dot = fileName.lastIndexOf('.');
    if (dot <= 0 || dot == fileName.length() - 1) {
      return Optional.empty();
    }
    return Optional.of(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
  }

  private static String fallbackMime(MediaType type) {
    return switch (type) {
      case VIDEO -> "video/mp4";
      case AUDIO -> "audio/mpeg";
      case IMAGE -> "image/jpeg";
      case DOCUMENT -> "application/octet-stream";
    };
  }
}
