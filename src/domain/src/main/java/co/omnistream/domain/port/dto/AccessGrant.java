package co.omnistream.domain.port.dto;

import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.UserId;
import java.util.Objects;

/**
 * Who has a permission on one file or collection, and at what level: a row of the access list that
 * {@code IAccessControl.accessTo} answers.
 *
 * <p>It names the user as well as identifying it, so whoever manages the resource can read the list
 * without being able to list the users of the server.
 */
public record AccessGrant(UserId user, String username, PermissionLevel level) {
  public AccessGrant {
    Objects.requireNonNull(user, "user");
    Objects.requireNonNull(username, "username");
    Objects.requireNonNull(level, "level");
    if (username.isBlank()) {
      throw new IllegalArgumentException("An access grant names its user");
    }
  }
}
