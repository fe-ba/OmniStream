package co.omnistream.domain.snapshot;

import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.sharing.SharedLinkId;
import java.time.Instant;

/**
 * State of a {@code SharedLink} (Memento, ADR 0011).
 *
 * @param id the link
 * @param token the secret that goes in the URL
 * @param collectionId the shared collection
 * @param guestId the guest user the link logs in as
 * @param level what the guest may do with the collection
 * @param createdBy who shared it
 * @param expiresAt from this instant on, the link no longer works
 * @param revoked whether someone revoked it before it expired
 */
public record SharedLinkSnapshot(
    SharedLinkId id,
    String token,
    ItemId collectionId,
    UserId guestId,
    PermissionLevel level,
    UserId createdBy,
    Instant expiresAt,
    boolean revoked) {}
