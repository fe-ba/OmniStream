package co.omnistream.infrastructure.event;

import co.omnistream.domain.port.out.IEventBus;
import co.omnistream.domain.port.out.contract.EventBusContract;

class EventBusAdapterContractTest extends EventBusContract {

  @Override
  protected IEventBus createBus() {
    return new EventBusAdapter();
  }
}
