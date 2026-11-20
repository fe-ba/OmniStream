package co.omnistream.domain.model.sharing;

import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.snapshot.SharedLinkSnapshot;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Root of the shared link aggregate: a link that lets whoever has it work with one collection, for
 * a limited time.
 *
 * <p>Sharing creates a guest user ({@code User.createGuest}) with permission on the collection, and
 * this link points to that guest. Whoever opens the link gets a session token for the guest, so
 * authorization works exactly as for any other user. The link only decides whether it can still be
 * used; when it cannot, the service refuses it with {@code ExpiredLink}.
 */
public final class SharedLink {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;

  private final SharedLinkId id;
  private final String token;
  private final ItemId collectionId;
  private final UserId guestId;
  private final PermissionLevel level;
  private final UserId createdBy;
  private final Instant expiresAt;
  private boolean revoked;

  private SharedLink(
      SharedLinkId id,
      String token,
      ItemId collectionId,
      UserId guestId,
      PermissionLevel level,
      UserId createdBy,
      Instant expiresAt,
      boolean revoked) {
    this.id = Objects.requireNonNull(id, "id");
    this.token = Objects.requireNonNull(token, "token");
    this.collectionId = Objects.requireNonNull(collectionId, "collectionId");
    this.guestId = Objects.requireNonNull(guestId, "guestId");
    this.level = Objects.requireNonNull(level, "level");
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    this.revoked = revoked;
  }

  /**
   * A new link that works until {@code now + lifetime}. Its token is 32 random bytes in URL-safe
   * Base64, so it can go in a URL and cannot be guessed.
   *
   * @param guest the guest user created for this link
   * @param level what the guest may do; the same level as the guest's permission
   * @throws IllegalArgumentException if {@code lifetime} is zero or negative
   */
  public static SharedLink create(
      ItemId collection,
      UserId guest,
      PermissionLevel level,
      UserId createdBy,
      Duration lifetime,
      Instant now) {
    if (!Objects.requireNonNull(lifetime, "lifetime").isPositive()) {
      throw new IllegalArgumentException("A link must last some time");
    }
    Objects.requireNonNull(now, "now");
    return new SharedLink(
        new SharedLinkId(UUID.randomUUID()),
        newToken(),
        collection,
        guest,
        level,
        createdBy,
        now.plus(lifetime),
        false);
  }

  /** Whether the link can be used at {@code now}: it was not revoked and has not expired. */
  public boolean isValidAt(Instant now) {
    Objects.requireNonNull(now, "now");
    return !revoked && now.isBefore(expiresAt);
  }

  /** From now on the link cannot be used. Revoking it again does nothing. */
  public void revoke() {
    revoked = true;
  }

  /** The link's current state (Memento, ADR 0011). */
  public SharedLinkSnapshot toSnapshot() {
    return new SharedLinkSnapshot(
        id, token, collectionId, guestId, level, createdBy, expiresAt, revoked);
  }

  /** Rebuilds a stored link exactly as it was, without checking the creation rules. */
  public static SharedLink fromSnapshot(SharedLinkSnapshot i) {
    Objects.requireNonNull(i, "i");
    return new SharedLink(
        i.id(),
        i.token(),
        i.collectionId(),
        i.guestId(),
        i.level(),
        i.createdBy(),
        i.expiresAt(),
        i.revoked());
  }

  /** Two links are the same if they have the same identifier. */
  @Override
  public boolean equals(Object other) {
    return other instanceof SharedLink link && id.equals(link.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  private static String newToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
