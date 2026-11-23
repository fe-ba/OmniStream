package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.port.out.ISessionRepository;
import co.omnistream.domain.port.out.contract.SessionRepositoryContract;

class InMemorySessionRepositoryTest extends SessionRepositoryContract {

  @Override
  protected ISessionRepository createRepository() {
    return new InMemorySessionRepository();
  }
}
