package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.port.out.IFileStorage;
import co.omnistream.domain.port.out.contract.FileStorageContract;

class InMemoryFileStorageTest extends FileStorageContract {

  @Override
  protected IFileStorage createStorage() {
    return new InMemoryFileStorage();
  }
}
