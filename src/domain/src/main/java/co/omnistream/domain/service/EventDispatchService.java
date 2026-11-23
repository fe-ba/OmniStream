package co.omnistream.domain.service;

import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.port.in.IEventDispatch;
import co.omnistream.domain.port.out.IEventPublisher;
import co.omnistream.domain.port.out.IOutboxRepository;
import java.util.List;
import java.util.Objects;

/** Domain service that implements the IEventDispatch driving port. */
public final class EventDispatchService implements IEventDispatch {
  private final IOutboxRepository outbox;
  private final IEventPublisher publisher;

  public EventDispatchService(IOutboxRepository outbox, IEventPublisher publisher) {
    this.outbox = Objects.requireNonNull(outbox, "outbox");
    this.publisher = Objects.requireNonNull(publisher, "publisher");
  }

  /**
   * Publishes at most {@code limit} pending events and marks them as published. An event is marked
   * only after the bus accepted it, so a failure leaves it pending for the next run: delivery is at
   * least once (Transactional Outbox).
   */
  @Override
  public int publishPending(int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("A limit must be positive");
    }
    List<DomainEvent> pending = outbox.fetchPending(limit);
    for (DomainEvent event : pending) {
      publisher.publish(event);
      outbox.markPublished(event.id());
    }
    return pending.size();
  }
}
