package co.omnistream.domain.service;

import co.omnistream.domain.port.in.IEventDispatch;
import co.omnistream.domain.port.out.IEventPublisher;
import co.omnistream.domain.port.out.IOutboxRepository;

/** Domain service that implements the IEventDispatch driving port. */
public final class EventDispatchService implements IEventDispatch {
  private final IOutboxRepository outbox;
  private final IEventPublisher publisher;

  public EventDispatchService(IOutboxRepository outbox, IEventPublisher publisher) {
    this.outbox = outbox;
    this.publisher = publisher;
  }

  @Override
  public int publishPending(int limit) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
