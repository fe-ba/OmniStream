package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.port.out.ISharedLinkRepository;
import co.omnistream.domain.port.out.contract.SharedLinkRepositoryContract;

class InMemorySharedLinkRepositoryTest extends SharedLinkRepositoryContract {

  @Override
  protected ISharedLinkRepository createRepository() {
    return new InMemorySharedLinkRepository();
  }
}
