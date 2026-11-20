package co.omnistream.domain.model.playback;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of a playback session ({@link PlaybackSession}).
 *
 * @param value the UUID behind the identifier
 */
public record SessionId(UUID value) {

  public SessionId {
    Objects.requireNonNull(value, "value");
  }
}
