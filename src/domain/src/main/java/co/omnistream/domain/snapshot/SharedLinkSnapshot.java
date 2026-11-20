package co.omnistream.domain.snapshot;

import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.sharing.SharedLinkId;
import java.time.Instant;

/** Immutable snapshot of an aggregate's state (Memento, ADR 0011). */
public record SharedLinkSnapshot(
    SharedLinkId id,
    String token,
    ItemId collectionId,
    UserId guestId,
    PermissionLevel level,
    UserId createdBy,
    Instant expiresAt,
    boolean revoked) {}
