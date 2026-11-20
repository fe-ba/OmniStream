package co.omnistream.domain.model.sharing;

import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.snapshot.SharedLinkSnapshot;
import java.time.Duration;
import java.time.Instant;

/** Aggregate root. */
public final class SharedLink {
  private SharedLinkId id;
  private String token;
  private ItemId collectionId;
  private UserId guestId;
  private PermissionLevel level;
  private UserId createdBy;
  private Instant expiresAt;
  private boolean revoked;

  private SharedLink() {}

  public static SharedLink create(
      ItemId collection,
      UserId guest,
      PermissionLevel level,
      UserId createdBy,
      Duration lifetime,
      Instant now) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public boolean isValidAt(Instant now) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void revoke() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public SharedLinkSnapshot toSnapshot() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public static SharedLink fromSnapshot(SharedLinkSnapshot i) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
