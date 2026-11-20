package co.omnistream.domain.event;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of a domain event.
 *
 * @param value the UUID behind the identifier
 */
public record EventId(UUID value) {

  public EventId {
    Objects.requireNonNull(value, "value");
  }

  /** A new, random identifier. */
  static EventId random() {
    return new EventId(UUID.randomUUID());
  }
}
