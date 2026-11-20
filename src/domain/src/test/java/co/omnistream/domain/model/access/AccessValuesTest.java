package co.omnistream.domain.model.access;

import static co.omnistream.domain.model.access.AccessFixtures.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.model.catalog.ItemId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessValuesTest {

  @Test
  void userIdsWithTheSameUuidAreEqual() {
    UUID uuid = UUID.randomUUID();

    assertThat(new UserId(uuid)).isEqualTo(new UserId(uuid));
  }

  @Test
  void userIdNeedsAUuid() {
    assertThatThrownBy(() -> new UserId(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void anIdentityIsStoredWithoutSurroundingSpaces() {
    LinkedIdentity identity = new LinkedIdentity(" local ", " ferney ");

    assertThat(identity).isEqualTo(new LinkedIdentity("local", "ferney"));
  }

  @Test
  void anIdentityNeedsAnIssuerAndASubject() {
    assertThatThrownBy(() -> new LinkedIdentity(" ", "ferney"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LinkedIdentity("local", ""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LinkedIdentity(null, "ferney"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void onlyTheLocalIssuerIsLocal() {
    assertThat(new LinkedIdentity("local", "ferney").isLocal()).isTrue();
    assertThat(new LinkedIdentity("https://accounts.example.com", "123").isLocal()).isFalse();
    assertThat(new LinkedIdentity(LinkedIdentity.SHARED_LINK_ISSUER, "abc").isLocal()).isFalse();
  }

  @Test
  void aPermissionNeedsAResourceAndALevel() {
    assertThatThrownBy(() -> new Permission(null, PermissionLevel.READ))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Permission(item(), null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void eachLevelAllowsItsAccessesAndThoseOfTheLevelsBelow() {
    // rows: READ, WRITE, MANAGE; columns: VIEW, DOWNLOAD, MODIFY, DELETE
    boolean[][] expected = {
      {true, true, false, false},
      {true, true, true, false},
      {true, true, true, true},
    };
    ItemId resource = item();
    for (PermissionLevel level : PermissionLevel.values()) {
      for (AccessType access : AccessType.values()) {
        boolean allowed = new Permission(resource, level).allows(access);

        assertThat(allowed).isEqualTo(expected[level.ordinal()][access.ordinal()]);
      }
    }
  }

  @Test
  void aPermissionAppliesOnlyToItsResource() {
    ItemId resource = item();
    Permission permission = new Permission(resource, PermissionLevel.READ);

    assertThat(permission.appliesTo(resource)).isTrue();
    assertThat(permission.appliesTo(item())).isFalse();
  }
}
