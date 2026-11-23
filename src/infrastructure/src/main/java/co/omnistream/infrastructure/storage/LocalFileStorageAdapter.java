package co.omnistream.infrastructure.storage;

import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.port.out.IFileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Secondary adapter that keeps the library on a local disk (ADR 0015). Every path is resolved
 * inside the permitted folder: a request that tries to leave it is refused, so a bug in a service
 * cannot read or delete files elsewhere.
 */
public class LocalFileStorageAdapter implements IFileStorage {

  private final Path root;

  public LocalFileStorageAdapter(Path root) {
    this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    createRoot();
  }

  /** The permitted folder. */
  public Path root() {
    return root;
  }

  @Override
  public InputStream read(Path path) {
    try {
      return Files.newInputStream(resolve(path));
    } catch (IOException e) {
      throw new StorageException("Cannot read " + path, e);
    }
  }

  @Override
  public void write(Path path, InputStream data) {
    Objects.requireNonNull(data, "data");
    Path target = resolve(path);
    try {
      createParents(target);
      Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new StorageException("Cannot write " + path, e);
    }
  }

  @Override
  public void delete(Path path) {
    try {
      Files.deleteIfExists(resolve(path));
    } catch (IOException e) {
      throw new StorageException("Cannot delete " + path, e);
    }
  }

  @Override
  public boolean exists(Path path) {
    return Files.exists(resolve(path));
  }

  @Override
  public List<Path> list(Path folder) {
    Path start = resolve(folder);
    try (Stream<Path> paths = Files.walk(start)) {
      return paths.filter(Files::isRegularFile).sorted().toList();
    } catch (IOException e) {
      throw new StorageException("Cannot list " + folder, e);
    }
  }

  @Override
  public Fingerprint computeFingerprint(Path path) {
    Path file = resolve(path);
    try (InputStream data = Files.newInputStream(file)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[64 * 1024];
      int read;
      while ((read = data.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
      return new Fingerprint("SHA-256", HexFormat.of().formatHex(digest.digest()));
    } catch (IOException e) {
      throw new StorageException("Cannot fingerprint " + path, e);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every JDK has SHA-256", e);
    }
  }

  @Override
  public Path copy(Path source, Path destination) {
    Path origin = resolve(source);
    Path target = resolve(destination);
    try {
      createParents(target);
      Files.copy(origin, target, StandardCopyOption.REPLACE_EXISTING);
      return target;
    } catch (IOException e) {
      throw new StorageException("Cannot copy " + source + " to " + destination, e);
    }
  }

  @Override
  public long freeSpace() {
    try {
      return Files.getFileStore(root).getUsableSpace();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read the free space of " + root, e);
    }
  }

  /** The absolute path of {@code path} inside the root, or {@link StorageException}. */
  protected Path resolve(Path path) {
    Objects.requireNonNull(path, "path");
    Path resolved = path.isAbsolute() ? path.normalize() : root.resolve(path).normalize();
    if (!resolved.startsWith(root)) {
      throw new StorageException("Path leaves the library: " + path);
    }
    return resolved;
  }

  private void createRoot() {
    try {
      Files.createDirectories(root);
    } catch (IOException e) {
      throw new StorageException("Cannot create the library folder " + root, e);
    }
  }

  private static void createParents(Path target) throws IOException {
    Path parent = target.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
  }
}
