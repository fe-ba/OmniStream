package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.port.out.IConfigurationRepository;
import java.nio.file.Path;
import java.util.Objects;

/** In-memory configuration repository for the domain tests (ADR 0018). */
public final class InMemoryConfigurationRepository implements IConfigurationRepository {

  private static final Path DEFAULT_LIBRARY_PATH =
      Path.of(System.getProperty("user.home"), ".omnistream", "library");

  private Path libraryPath = DEFAULT_LIBRARY_PATH;

  @Override
  public Path getLibraryPath() {
    return libraryPath;
  }

  @Override
  public void saveLibraryPath(Path path) {
    libraryPath = Objects.requireNonNull(path, "path");
  }
}
