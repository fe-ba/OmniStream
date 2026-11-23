package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.port.out.IDataBackup;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** In-memory data backup for the domain tests (ADR 0018). */
public final class InMemoryDataBackup implements IDataBackup {

  private final List<Path> backups = new ArrayList<>();

  @Override
  public Path backup(Path destination) {
    backups.add(Objects.requireNonNull(destination, "destination"));
    return destination;
  }

  /** The destinations of the backups made, in order. */
  public List<Path> backups() {
    return List.copyOf(backups);
  }
}
