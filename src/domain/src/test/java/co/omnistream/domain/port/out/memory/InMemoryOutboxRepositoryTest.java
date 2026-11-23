package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.IOutboxRepository;
import co.omnistream.domain.port.out.contract.OutboxRepositoryContract;

class InMemoryOutboxRepositoryTest extends OutboxRepositoryContract {

  private final InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
  private final InMemoryCatalogRepository catalog = new InMemoryCatalogRepository(outbox);

  @Override
  protected ICatalogRepository createCatalog() {
    return catalog;
  }

  @Override
  protected IOutboxRepository createOutbox() {
    return outbox;
  }
}
