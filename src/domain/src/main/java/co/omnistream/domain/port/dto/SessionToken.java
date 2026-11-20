package co.omnistream.domain.port.dto;

import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.UserId;
import java.time.Instant;

/** Data transfer object of the ports. */
public record SessionToken(String value, UserId userId, Role role, Instant expiresAt) {
  public boolean isValidAt(Instant now) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
