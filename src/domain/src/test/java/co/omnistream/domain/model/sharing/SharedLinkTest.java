package co.omnistream.domain.model.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.snapshot.SharedLinkSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SharedLinkTest {

  private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
  private static final Duration WEEK = Duration.ofDays(7);

  private final ItemId collection = new ItemId(UUID.randomUUID());
  private final UserId guest = new UserId(UUID.randomUUID());
  private final UserId owner = new UserId(UUID.randomUUID());

  private SharedLink forAWeek() {
    return SharedLink.create(collection, guest, PermissionLevel.READ, owner, WEEK, NOW);
  }

  @Test
  void aNewLinkPointsToItsGuestAndExpiresAfterItsLifetime() {
    SharedLinkSnapshot link = forAWeek().toSnapshot();

    assertThat(link.collectionId()).isEqualTo(collection);
    assertThat(link.guestId()).isEqualTo(guest);
    assertThat(link.level()).isEqualTo(PermissionLevel.READ);
    assertThat(link.createdBy()).isEqualTo(owner);
    assertThat(link.expiresAt()).isEqualTo(NOW.plus(WEEK));
    assertThat(link.revoked()).isFalse();
  }

  @Test
  void theTokenIsLongRandomAndSafeInAUrl() {
    String token = forAWeek().toSnapshot().token();

    assertThat(token.length()).isEqualTo(43);
    assertThat(token.matches("[A-Za-z0-9_-]+")).isTrue();
    assertThat(forAWeek().toSnapshot().token()).isNotEqualTo(token);
  }

  @Test
  void aLinkMustLastSomeTime() {
    assertThatThrownBy(() ->
            SharedLink.create(collection, guest, PermissionLevel.READ, owner, Duration.ZERO, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            SharedLink.create(
                collection, guest, PermissionLevel.READ, owner, Duration.ofHours(-1), NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aLinkNeedsAllItsParts() {
    assertThatThrownBy(() -> SharedLink.create(null, guest, PermissionLevel.READ, owner, WEEK, NOW))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> SharedLink.create(collection, guest, null, owner, WEEK, NOW))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> SharedLink.create(collection, guest, PermissionLevel.READ, owner, WEEK, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void aLinkWorksUntilItExpires() {
    SharedLink link = forAWeek();

    assertThat(link.isValidAt(NOW)).isTrue();
    assertThat(link.isValidAt(NOW.plus(WEEK).minusMillis(1))).isTrue();
    assertThat(link.isValidAt(NOW.plus(WEEK))).isFalse();
    assertThat(link.isValidAt(NOW.plus(Duration.ofDays(30)))).isFalse();
  }

  @Test
  void aRevokedLinkNoLongerWorks() {
    SharedLink link = forAWeek();

    link.revoke();
    link.revoke();

    assertThat(link.isValidAt(NOW)).isFalse();
    assertThat(link.toSnapshot().revoked()).isTrue();
  }

  @Test
  void aLinkRebuiltFromItsSnapshotIsTheSame() {
    SharedLink link = forAWeek();
    link.revoke();

    SharedLink rebuilt = SharedLink.fromSnapshot(link.toSnapshot());

    assertThat(rebuilt).isEqualTo(link);
    assertThat(rebuilt.toSnapshot()).isEqualTo(link.toSnapshot());
  }

  @Test
  void eachLinkGetsItsOwnIdentifier() {
    assertThat(forAWeek()).isNotEqualTo(forAWeek());
  }

  @Test
  void sharedLinkIdNeedsAUuid() {
    assertThatThrownBy(() -> new SharedLinkId(null)).isInstanceOf(NullPointerException.class);
  }
}
