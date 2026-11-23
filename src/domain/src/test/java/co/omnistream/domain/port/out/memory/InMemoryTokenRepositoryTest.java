package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.port.out.ITokenRepository;
import co.omnistream.domain.port.out.contract.TokenRepositoryContract;

class InMemoryTokenRepositoryTest extends TokenRepositoryContract {

  @Override
  protected ITokenRepository createRepository() {
    return new InMemoryTokenRepository();
  }
}
