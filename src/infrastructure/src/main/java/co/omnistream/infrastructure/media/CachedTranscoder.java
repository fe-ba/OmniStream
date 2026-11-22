package co.omnistream.infrastructure.media;

import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.StreamProfile;
import co.omnistream.domain.port.out.ITranscoder;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Decorator of the transcoder that keeps the converted files on disk (Decorator, ADR 0015).
 *
 * <p>Converting the same file for the same device again is common, for example when someone goes
 * back in a video: the second time the bytes come from the cache. The cache is bounded; when it
 * grows past its limit, the least recently used files are deleted first.
 */
public final class CachedTranscoder implements ITranscoder {

  private static final String MIME_SUFFIX = ".mime";
  private static final String DATA_SUFFIX = ".media";

  private final ITranscoder delegate;
  private final Path cacheFolder;
  private final long limitBytes;

  public CachedTranscoder(ITranscoder delegate, Path cacheFolder, long limitBytes) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.cacheFolder = Objects.requireNonNull(cacheFolder, "cacheFolder");
    if (limitBytes <= 0) {
      throw new IllegalArgumentException("The cache needs a positive size");
    }
    this.limitBytes = limitBytes;
  }

  @Override
  public boolean supports(MediaType source, StreamProfile p) {
    return delegate.supports(source, p);
  }

  @Override
  public synchronized MediaStream transcode(Path path, StreamProfile p, Duration pos) {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(p, "p");
    Objects.requireNonNull(pos, "pos");
    String key = key(path, p, pos);
    Path data = cacheFolder.resolve(key + DATA_SUFFIX);
    Path mime = cacheFolder.resolve(key + MIME_SUFFIX);
    if (Files.isRegularFile(data) && Files.isRegularFile(mime)) {
      try {
        Files.setLastModifiedTime(
            data, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
        return new MediaStream(
            Files.newInputStream(data), readMime(mime), true, Optional.of(Files.size(data)));
      } catch (IOException e) {
        throw new UncheckedIOException("Cannot read the transcoding cache", e);
      }
    }

    MediaStream produced = delegate.transcode(path, p, pos);
    try (InputStream bytes = produced.data()) {
      Files.createDirectories(cacheFolder);
      Path temporary = Files.createTempFile(cacheFolder, "omnistream-", ".part");
      try {
        Files.copy(bytes, temporary, StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(mime, produced.mime(), StandardCharsets.UTF_8);
        Files.move(temporary, data, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        Files.deleteIfExists(temporary);
        throw new UncheckedIOException("Cannot write the transcoding cache", e);
      }
      evict(data);
      return new MediaStream(
          Files.newInputStream(data), produced.mime(), true, Optional.of(Files.size(data)));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot cache the transcoded file", e);
    }
  }

  /** The name of the cached copy: the file, its profile and the position, hashed. */
  static String key(Path path, StreamProfile p, Duration pos) {
    String wanted =
        path.toAbsolutePath().normalize()
            + "|"
            + p.codec().orElse("")
            + "|"
            + p.bitrateKbps().map(String::valueOf).orElse("")
            + "|"
            + p.resolution().orElse("")
            + "|"
            + pos.toNanos();
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(wanted.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every JDK has SHA-256", e);
    }
  }

  /** Deletes the oldest files until the cache fits in its limit, keeping {@code keep}. */
  private void evict(Path keep) {
    if (!Files.isDirectory(cacheFolder)) {
      return;
    }
    List<Path> files;
    try (Stream<Path> paths = Files.list(cacheFolder)) {
      files =
          paths
              .filter(file -> file.getFileName().toString().endsWith(DATA_SUFFIX))
              .filter(file -> !file.equals(keep))
              .sorted(Comparator.comparingLong(CachedTranscoder::lastModified))
              .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot inspect the transcoding cache", e);
    }
    long total = sizeOf(keep);
    for (Path file : files) {
      total += sizeOf(file);
    }
    for (Path file : files) {
      if (total <= limitBytes) {
        return;
      }
      try {
        long size = Files.size(file);
        Files.deleteIfExists(file);
        Files.deleteIfExists(mimeOf(file));
        total -= size;
      } catch (IOException e) {
        throw new UncheckedIOException("Cannot evict from the transcoding cache", e);
      }
    }
  }

  private static long sizeOf(Path file) {
    try {
      return Files.size(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot size " + file, e);
    }
  }

  private static Path mimeOf(Path data) {
    String name = data.getFileName().toString();
    String base = name.substring(0, name.length() - DATA_SUFFIX.length());
    return data.resolveSibling(base + MIME_SUFFIX);
  }

  private static String readMime(Path file) throws IOException {
    return Files.readString(file, StandardCharsets.UTF_8).strip();
  }

  private static long lastModified(Path file) {
    try {
      return Files.getLastModifiedTime(file).toMillis();
    } catch (IOException e) {
      return 0;
    }
  }
}
