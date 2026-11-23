package co.omnistream.infrastructure.token;

import co.omnistream.domain.port.out.ITokenRepository;
import co.omnistream.domain.port.out.contract.TokenRepositoryContract;
import java.time.Clock;

class InMemoryTokenAdapterContractTest extends TokenRepositoryContract {

  @Override
  protected ITokenRepository createRepository() {
    return new InMemoryTokenAdapter(Clock.systemUTC());
  }
}
