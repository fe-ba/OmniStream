package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.port.out.IConfigurationRepository;
import co.omnistream.domain.port.out.contract.ConfigurationRepositoryContract;

class InMemoryConfigurationRepositoryTest extends ConfigurationRepositoryContract {

  @Override
  protected IConfigurationRepository createRepository() {
    return new InMemoryConfigurationRepository();
  }
}
