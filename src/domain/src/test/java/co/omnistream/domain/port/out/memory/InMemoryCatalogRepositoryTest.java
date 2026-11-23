package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.contract.CatalogRepositoryContract;

class InMemoryCatalogRepositoryTest extends CatalogRepositoryContract {

  @Override
  protected ICatalogRepository createRepository() {
    return new InMemoryCatalogRepository();
  }
}
