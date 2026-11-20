package co.omnistream.domain.model.access;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of a user ({@link User}).
 *
 * <p>Other aggregates (shared links, playback sessions) refer to users only through this
 * identifier.
 *
 * @param value the UUID behind the identifier
 */
public record UserId(UUID value) {

  public UserId {
    Objects.requireNonNull(value, "value");
  }
}
