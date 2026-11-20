package co.omnistream.domain.snapshot;

import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.UserId;
import java.util.Set;

/** Immutable snapshot of an aggregate's state (Memento, ADR 0011). */
public record UserSnapshot(
    UserId id,
    String username,
    Role role,
    Set<Permission> permissions,
    Set<LinkedIdentity> identities,
    boolean active) {}
