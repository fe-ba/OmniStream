package co.omnistream.domain.port.out;

import java.nio.file.Path;

/** Driven port: defined by the domain, implemented by secondary adapters. */
public interface IConfigurationRepository {
  Path getLibraryPath();

  void saveLibraryPath(Path path);
}
