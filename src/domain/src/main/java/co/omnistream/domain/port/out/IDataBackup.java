package co.omnistream.domain.port.out;

import java.nio.file.Path;

/** Driven port: defined by the domain, implemented by secondary adapters. */
public interface IDataBackup {
  Path backup(Path destination);
}
