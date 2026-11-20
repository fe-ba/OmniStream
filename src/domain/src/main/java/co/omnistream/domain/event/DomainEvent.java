package co.omnistream.domain.event;

import java.time.Instant;
import java.util.Objects;

/**
 * Something that happened in the domain. Aggregates record events; the persistence adapter writes
 * them to the outbox in the same transaction as the aggregate (Transactional Outbox), and the event
 * bus delivers them to the observers.
 *
 * <p>Events are immutable and equal when their identifiers are equal.
 */
public abstract class DomainEvent {

  private final EventId id;
  private final Instant occurredAt;

  /**
   * Rebuilds or creates an event with a known identifier, for example when read from the outbox.
   */
  protected DomainEvent(EventId id, Instant occurredAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
  }

  /** Identifier of this event. */
  public EventId id() {
    return id;
  }

  /** When it happened. */
  public Instant occurredAt() {
    return occurredAt;
  }

  /** Name of the event type, as stored in the outbox: the simple class name. */
  public String type() {
    return getClass().getSimpleName();
  }

  @Override
  public final boolean equals(Object other) {
    return other instanceof DomainEvent event && id.equals(event.id);
  }

  @Override
  public final int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return type() + "[" + id.value() + " at " + occurredAt + "]";
  }
}
