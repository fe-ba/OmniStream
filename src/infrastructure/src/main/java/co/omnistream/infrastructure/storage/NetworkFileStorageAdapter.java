package co.omnistream.infrastructure.storage;

import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.port.out.IFileStorage;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Secondary adapter for a library mounted over the network (NAS, SMB). It behaves like {@link
 * LocalFileStorageAdapter} and retries once when the mount is momentarily gone, which is what makes
 * a network folder different from a local disk.
 *
 * <p>It belongs to version 2.0: the first version only uses the local disk (ADR 0009).
 */
public final class NetworkFileStorageAdapter implements IFileStorage {

  private static final int ATTEMPTS = 2;

  private final LocalFileStorageAdapter local;

  public NetworkFileStorageAdapter(Path mountPoint) {
    this.local = new LocalFileStorageAdapter(mountPoint);
  }

  /** The mount point. */
  public Path mountPoint() {
    return local.root();
  }

  @Override
  public InputStream read(Path path) {
    return retry(() -> local.read(path));
  }

  @Override
  public void write(Path path, InputStream data) {
    Objects.requireNonNull(data, "data");
    retry(() -> {
      local.write(path, data);
      return null;
    });
  }

  @Override
  public void delete(Path path) {
    retry(() -> {
      local.delete(path);
      return null;
    });
  }

  @Override
  public boolean exists(Path path) {
    return retry(() -> local.exists(path));
  }

  @Override
  public List<Path> list(Path folder) {
    return retry(() -> local.list(folder));
  }

  @Override
  public Fingerprint computeFingerprint(Path path) {
    return retry(() -> local.computeFingerprint(path));
  }

  @Override
  public Path copy(Path source, Path destination) {
    return retry(() -> local.copy(source, destination));
  }

  @Override
  public long freeSpace() {
    return retry(local::freeSpace);
  }

  /** Retries the work once: a mount often fails while it reconnects. */
  private static <T> T retry(Supplier<T> work) {
    RuntimeException last = null;
    for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
      try {
        return work.get();
      } catch (RuntimeException e) {
        last = e;
      }
    }
    throw Objects.requireNonNull(last, "last");
  }
}
