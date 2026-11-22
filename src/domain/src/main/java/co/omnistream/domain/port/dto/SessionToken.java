package co.omnistream.domain.port.dto;

import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.UserId;
import java.time.Instant;
import java.util.Objects;

/** Data transfer object of the ports. */
public record SessionToken(String value, UserId userId, Role role, Instant expiresAt) {
  public SessionToken {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(expiresAt, "expiresAt");
    if (value.isBlank()) {
      throw new IllegalArgumentException("A session token needs a value");
    }
  }

  /** Whether the token still identifies its user at {@code now}. */
  public boolean isValidAt(Instant now) {
    return Objects.requireNonNull(now, "now").isBefore(expiresAt);
  }
}
