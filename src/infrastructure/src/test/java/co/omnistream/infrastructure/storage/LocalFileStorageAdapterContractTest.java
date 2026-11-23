package co.omnistream.infrastructure.storage;

import co.omnistream.domain.port.out.IFileStorage;
import co.omnistream.domain.port.out.contract.FileStorageContract;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageAdapterContractTest extends FileStorageContract {

  @TempDir Path temporary;

  private LocalFileStorageAdapter storage;

  @BeforeEach
  void openLibrary() {
    storage = new LocalFileStorageAdapter(temporary.resolve("library"));
  }

  @Override
  protected IFileStorage createStorage() {
    return storage;
  }
}
