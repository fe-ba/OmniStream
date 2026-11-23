package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.port.out.IFileStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory file storage for the domain tests (ADR 0018).
 *
 * <p>It keeps the bytes of every file in a map. Paths are compared after normalising them, and a
 * path that tries to leave the library with {@code ..} is refused, as the real adapter does.
 */
public final class InMemoryFileStorage implements IFileStorage {

  private static final long DEFAULT_FREE_SPACE = 1_000_000_000L;

  private final Map<Path, byte[]> files = new LinkedHashMap<>();
  private final long freeSpace;

  public InMemoryFileStorage() {
    this(DEFAULT_FREE_SPACE);
  }

  public InMemoryFileStorage(long freeSpace) {
    if (freeSpace < 0) {
      throw new IllegalArgumentException("The free space cannot be negative");
    }
    this.freeSpace = freeSpace;
  }

  @Override
  public InputStream read(Path path) {
    return new ByteArrayInputStream(data(path));
  }

  @Override
  public void write(Path path, InputStream data) {
    Objects.requireNonNull(data, "data");
    try {
      files.put(key(path), data.readAllBytes());
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write " + path, e);
    }
  }

  @Override
  public void delete(Path path) {
    files.remove(key(path));
  }

  @Override
  public boolean exists(Path path) {
    return files.containsKey(key(path));
  }

  @Override
  public List<Path> list(Path root) {
    Path folder = key(root);
    return files.keySet().stream()
        .filter(path -> path.startsWith(folder) && !path.equals(folder))
        .sorted()
        .toList();
  }

  @Override
  public Fingerprint computeFingerprint(Path path) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return new Fingerprint("SHA-256", HexFormat.of().formatHex(digest.digest(data(path))));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every JDK has SHA-256", e);
    }
  }

  @Override
  public Path copy(Path source, Path destination) {
    Path target = key(destination);
    files.put(target, data(source).clone());
    return target;
  }

  @Override
  public long freeSpace() {
    return freeSpace;
  }

  /** The number of files kept, for the assertions of a test. */
  public int size() {
    return files.size();
  }

  private byte[] data(Path path) {
    byte[] content = files.get(key(path));
    if (content == null) {
      throw new IllegalArgumentException("No file " + path);
    }
    return content;
  }

  private static Path key(Path path) {
    Path normalized = Objects.requireNonNull(path, "path").normalize();
    if (normalized.startsWith("..")) {
      throw new IllegalArgumentException("Path leaves the library: " + path);
    }
    return normalized;
  }
}
