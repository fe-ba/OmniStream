package co.omnistream.domain.policy;

import static co.omnistream.domain.model.access.AccessFixtures.item;
import static co.omnistream.domain.model.access.AccessFixtures.local;
import static co.omnistream.domain.model.access.AccessFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.snapshot.UserSnapshot;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessPolicyTest {

  private final AccessPolicy policy = new AccessPolicy();
  private final ItemId resource = item();

  @Test
  void anAdministratorMayDoEverythingWithoutPermissions() {
    User admin = user("root", Role.ADMIN);

    for (AccessType access : AccessType.values()) {
      assertThat(policy.authorizes(admin, resource, access)).isTrue();
    }
    assertThat(policy.effectiveLevel(admin, resource)).contains(PermissionLevel.MANAGE);
  }

  @Test
  void aUserWithoutAPermissionOnTheResourceMayDoNothing() {
    User user = user("ana", Role.USER);
    user.grant(new Permission(item(), PermissionLevel.MANAGE));

    for (AccessType access : AccessType.values()) {
      assertThat(policy.authorizes(user, resource, access)).isFalse();
    }
    assertThat(policy.effectiveLevel(user, resource)).isEmpty();
  }

  @Test
  void aUserMayDoWhatItsLevelAllows() {
    for (PermissionLevel level : PermissionLevel.values()) {
      User user = user("ana", Role.USER);
      Permission permission = new Permission(resource, level);
      user.grant(permission);

      for (AccessType access : AccessType.values()) {
        assertThat(policy.authorizes(user, resource, access)).isEqualTo(permission.allows(access));
      }
      assertThat(policy.effectiveLevel(user, resource)).contains(level);
    }
  }

  @Test
  void aGuestFollowsTheSameRulesWithThePermissionOfItsLink() {
    User guest = User.createGuest(resource, PermissionLevel.READ);

    assertThat(policy.authorizes(guest, resource, AccessType.VIEW)).isTrue();
    assertThat(policy.authorizes(guest, resource, AccessType.DOWNLOAD)).isTrue();
    assertThat(policy.authorizes(guest, resource, AccessType.MODIFY)).isFalse();
    assertThat(policy.authorizes(guest, item(), AccessType.VIEW)).isFalse();
    assertThat(policy.effectiveLevel(guest, resource)).contains(PermissionLevel.READ);
  }

  @Test
  void aDeactivatedUserMayDoNothingWhateverItsRole() {
    for (Role role : new Role[] {Role.ADMIN, Role.USER}) {
      User user = user("ana", role);
      user.grant(new Permission(resource, PermissionLevel.MANAGE));
      user.deactivate();

      assertThat(policy.authorizes(user, resource, AccessType.VIEW)).isFalse();
      assertThat(policy.effectiveLevel(user, resource)).isEmpty();
    }
  }

  @Test
  void theEffectiveLevelIsTheHighestOneOnTheResource() {
    // Only a stored snapshot can hold two permissions on the same resource.
    UserSnapshot stored =
        new UserSnapshot(
            new UserId(UUID.randomUUID()),
            "ana",
            Role.USER,
            Set.of(
                new Permission(resource, PermissionLevel.READ),
                new Permission(resource, PermissionLevel.WRITE)),
            Set.of(local("ana")),
            true);
    User user = User.fromSnapshot(stored);

    assertThat(policy.effectiveLevel(user, resource)).contains(PermissionLevel.WRITE);
    assertThat(policy.authorizes(user, resource, AccessType.MODIFY)).isTrue();
  }

  @Test
  void authorizesAgreesWithTheEffectiveLevelInEveryCase() {
    for (Role role : new Role[] {Role.ADMIN, Role.USER}) {
      for (PermissionLevel level : PermissionLevel.values()) {
        for (boolean active : new boolean[] {true, false}) {
          User user = user("ana", role);
          user.grant(new Permission(resource, level));
          if (!active) {
            user.deactivate();
          }
          for (AccessType access : AccessType.values()) {
            boolean expected =
                policy
                    .effectiveLevel(user, resource)
                    .map(effective -> new Permission(resource, effective).allows(access))
                    .orElse(false);

            assertThat(policy.authorizes(user, resource, access)).isEqualTo(expected);
            assertThat(user.canAccess(resource, access)).isEqualTo(expected);
          }
        }
      }
    }
  }
}
