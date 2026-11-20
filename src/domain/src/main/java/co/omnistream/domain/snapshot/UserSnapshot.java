package co.omnistream.domain.snapshot;

import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.UserId;
import java.util.Set;

/**
 * State of a {@code User} (Memento, ADR 0011).
 *
 * @param id the user
 * @param username name shown to others
 * @param role administrator, regular user or guest
 * @param permissions at most one per resource
 * @param identities the identities it logs in with; at least one
 * @param active false once the user has been deactivated
 */
public record UserSnapshot(
    UserId id,
    String username,
    Role role,
    Set<Permission> permissions,
    Set<LinkedIdentity> identities,
    boolean active) {

  public UserSnapshot {
    permissions = Set.copyOf(permissions);
    identities = Set.copyOf(identities);
  }
}
