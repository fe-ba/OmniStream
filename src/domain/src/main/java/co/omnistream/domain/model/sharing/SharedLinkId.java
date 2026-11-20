package co.omnistream.domain.model.sharing;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of a shared link ({@link SharedLink}).
 *
 * @param value the UUID behind the identifier
 */
public record SharedLinkId(UUID value) {

  public SharedLinkId {
    Objects.requireNonNull(value, "value");
  }
}
