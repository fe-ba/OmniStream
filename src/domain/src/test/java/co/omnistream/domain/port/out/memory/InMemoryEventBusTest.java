package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.port.out.IEventBus;
import co.omnistream.domain.port.out.contract.EventBusContract;

class InMemoryEventBusTest extends EventBusContract {

  @Override
  protected IEventBus createBus() {
    return new InMemoryEventBus();
  }
}
