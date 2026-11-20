package co.omnistream.domain.event;

import java.time.Instant;

/** Domain event. */
public abstract class DomainEvent {
  private EventId id;
  private Instant occurredAt;

  public String type() {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
