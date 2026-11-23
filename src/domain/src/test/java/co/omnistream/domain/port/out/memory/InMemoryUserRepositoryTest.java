package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.port.out.contract.UserRepositoryContract;

class InMemoryUserRepositoryTest extends UserRepositoryContract {

  @Override
  protected IUserRepository createRepository() {
    return new InMemoryUserRepository();
  }
}
