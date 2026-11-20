package co.omnistream.domain.model.access;

import static co.omnistream.domain.model.access.AccessFixtures.item;
import static co.omnistream.domain.model.access.AccessFixtures.local;
import static co.omnistream.domain.model.access.AccessFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.snapshot.UserSnapshot;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

  private final ItemId resource = item();

  // ---------------------------------------------------------------- creation

  @Test
  void aNewUserIsActiveWithItsIdentityAndNoPermissions() {
    UserSnapshot user = User.create("  ferney ", Role.USER, local("ferney")).toSnapshot();

    assertThat(user.username()).isEqualTo("ferney");
    assertThat(user.role()).isEqualTo(Role.USER);
    assertThat(user.active()).isTrue();
    assertThat(user.permissions()).isEmpty();
    assertThat(user.identities()).containsExactly(local("ferney"));
  }

  @Test
  void aUserNeedsAUsername() {
    assertThatThrownBy(() -> User.create(" ", Role.USER, local("x")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createDoesNotMakeGuests() {
    assertThatThrownBy(() -> User.create("ana", Role.GUEST, local("ana")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aGuestHasOnlyThePermissionOfItsLink() {
    UserSnapshot guest = User.createGuest(resource, PermissionLevel.READ).toSnapshot();

    assertThat(guest.role()).isEqualTo(Role.GUEST);
    assertThat(guest.active()).isTrue();
    assertThat(guest.permissions()).containsExactly(new Permission(resource, PermissionLevel.READ));
    assertThat(guest.username()).isEqualTo("guest-" + guest.id().value());
    assertThat(guest.identities())
        .containsExactly(
            new LinkedIdentity(LinkedIdentity.SHARED_LINK_ISSUER, guest.id().value().toString()));
  }

  @Test
  void eachUserGetsItsOwnIdentifier() {
    assertThat(user("ana", Role.USER)).isNotEqualTo(user("ana", Role.USER));
  }

  // ---------------------------------------------------------------- permissions

  @Test
  void aGrantedPermissionGivesAccess() {
    User user = user("ana", Role.USER);

    user.grant(new Permission(resource, PermissionLevel.WRITE));

    assertThat(user.canAccess(resource, AccessType.MODIFY)).isTrue();
    assertThat(user.canAccess(resource, AccessType.DELETE)).isFalse();
    assertThat(user.canAccess(item(), AccessType.VIEW)).isFalse();
  }

  @Test
  void grantingOnTheSameResourceReplacesTheLevel() {
    User user = user("ana", Role.USER);
    user.grant(new Permission(resource, PermissionLevel.MANAGE));

    user.grant(new Permission(resource, PermissionLevel.READ));

    assertThat(user.toSnapshot().permissions())
        .containsExactly(new Permission(resource, PermissionLevel.READ));
    assertThat(user.canAccess(resource, AccessType.DELETE)).isFalse();
  }

  @Test
  void permissionsOnDifferentResourcesAreKept() {
    User user = user("ana", Role.USER);
    ItemId other = item();

    user.grant(new Permission(resource, PermissionLevel.READ));
    user.grant(new Permission(other, PermissionLevel.WRITE));

    assertThat(user.toSnapshot().permissions()).hasSize(2);
  }

  @Test
  void aRevokedPermissionNoLongerGivesAccess() {
    User user = user("ana", Role.USER);
    Permission permission = new Permission(resource, PermissionLevel.READ);
    user.grant(permission);

    user.revoke(permission);

    assertThat(user.canAccess(resource, AccessType.VIEW)).isFalse();
  }

  @Test
  void revokingAPermissionTheUserDoesNotHaveDoesNothing() {
    User user = user("ana", Role.USER);
    user.grant(new Permission(resource, PermissionLevel.WRITE));

    user.revoke(new Permission(resource, PermissionLevel.READ));

    assertThat(user.toSnapshot().permissions())
        .containsExactly(new Permission(resource, PermissionLevel.WRITE));
  }

  // ---------------------------------------------------------------- role, identities, state

  @Test
  void aUserCanBecomeAnAdministratorAndBack() {
    User user = user("ana", Role.USER);

    user.changeRole(Role.ADMIN);
    assertThat(user.isAdmin()).isTrue();

    user.changeRole(Role.USER);
    assertThat(user.isAdmin()).isFalse();
  }

  @Test
  void nobodyCanBecomeAGuest() {
    User user = user("ana", Role.USER);

    assertThatThrownBy(() -> user.changeRole(Role.GUEST))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void whatComesFromTheLinkCannotChangeInAGuest() {
    User guest = User.createGuest(resource, PermissionLevel.READ);
    Permission more = new Permission(resource, PermissionLevel.MANAGE);

    assertThatThrownBy(() -> guest.grant(more)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> guest.revoke(new Permission(resource, PermissionLevel.READ)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> guest.changeRole(Role.USER)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> guest.link(local("guest"))).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aUserCanLinkMoreIdentities() {
    User user = user("ana", Role.USER);
    LinkedIdentity oidc = new LinkedIdentity("https://accounts.example.com", "123");

    user.link(oidc);
    user.link(oidc);

    assertThat(user.toSnapshot().identities()).containsExactlyInAnyOrder(local("ana"), oidc);
  }

  @Test
  void aDeactivatedUserCanDoNothing() {
    User user = user("ana", Role.USER);
    user.grant(new Permission(resource, PermissionLevel.MANAGE));

    user.deactivate();

    assertThat(user.toSnapshot().active()).isFalse();
    assertThat(user.canAccess(resource, AccessType.VIEW)).isFalse();
  }

  @Test
  void aDeactivatedAdministratorIsNotAnAdministrator() {
    User admin = user("root", Role.ADMIN);

    admin.deactivate();

    assertThat(admin.isAdmin()).isFalse();
    assertThat(admin.canAccess(resource, AccessType.VIEW)).isFalse();
  }

  // ---------------------------------------------------------------- snapshots

  @Test
  void aUserRebuiltFromItsSnapshotIsTheSame() {
    User user = user("ana", Role.USER);
    user.grant(new Permission(resource, PermissionLevel.WRITE));
    user.link(new LinkedIdentity("https://accounts.example.com", "123"));
    user.deactivate();

    User rebuilt = User.fromSnapshot(user.toSnapshot());

    assertThat(rebuilt).isEqualTo(user);
    assertThat(rebuilt.toSnapshot()).isEqualTo(user.toSnapshot());
  }

  @Test
  void aSnapshotDoesNotChangeWhenTheUserDoes() {
    User user = user("ana", Role.USER);
    UserSnapshot before = user.toSnapshot();

    user.grant(new Permission(resource, PermissionLevel.READ));

    assertThat(before.permissions()).isEmpty();
  }

  @Test
  void theUserDoesNotChangeWhenTheSetsOfItsSnapshotDo() {
    Set<Permission> permissions = new HashSet<>();
    UserSnapshot snapshot =
        new UserSnapshot(
            new UserId(UUID.randomUUID()),
            "ana",
            Role.USER,
            permissions,
            Set.of(local("ana")),
            true);
    User user = User.fromSnapshot(snapshot);

    permissions.add(new Permission(resource, PermissionLevel.READ));

    assertThat(user.canAccess(resource, AccessType.VIEW)).isFalse();
  }
}
