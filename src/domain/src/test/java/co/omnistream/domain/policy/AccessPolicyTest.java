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
import co.omnistream.domain.model.catalog.CatalogId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import co.omnistream.domain.snapshot.UserSnapshot;
import java.util.List;
import java.util.Optional;
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

  // ------------------------------------------------------------------ delegation (ADR 0024)

  private static final Optional<PermissionLevel> NOTHING_INHERITED = Optional.empty();

  private User managerOf(ItemId resource) {
    User manager = user("marta", Role.USER);
    manager.grant(new Permission(resource, PermissionLevel.MANAGE));
    return manager;
  }

  @Test
  void anAdministratorMayDelegateAnyLevelOnAnyResource() {
    User admin = user("root", Role.ADMIN);
    User target = user("ana", Role.USER);

    for (PermissionLevel level : PermissionLevel.values()) {
      Permission permission = new Permission(item(), level);
      assertThat(policy.mayDelegate(admin, NOTHING_INHERITED, target, permission)).isTrue();
    }
  }

  @Test
  void aManagerMayHandOutReadAndWriteButNotManage() {
    User manager = managerOf(resource);
    User target = user("ana", Role.USER);

    assertThat(policy.mayDelegate(manager, NOTHING_INHERITED, target, readOn(resource))).isTrue();
    assertThat(policy.mayDelegate(manager, NOTHING_INHERITED, target, writeOn(resource))).isTrue();
    assertThat(
            policy.mayDelegate(
                manager,
                NOTHING_INHERITED,
                target,
                new Permission(resource, PermissionLevel.MANAGE)))
        .isFalse();
  }

  @Test
  void managingThroughACollectionCountsAsManaging() {
    User manager = user("marta", Role.USER); // nothing on the file itself
    User target = user("ana", Role.USER);

    assertThat(
            policy.mayDelegate(
                manager, Optional.of(PermissionLevel.MANAGE), target, writeOn(resource)))
        .isTrue();
    assertThat(
            policy.mayDelegate(
                manager, Optional.of(PermissionLevel.WRITE), target, readOn(resource)))
        .isFalse();
  }

  @Test
  void writingIsNotEnoughToDelegate() {
    User writer = user("marta", Role.USER);
    writer.grant(writeOn(resource));

    assertThat(
            policy.mayDelegate(writer, NOTHING_INHERITED, user("ana", Role.USER), readOn(resource)))
        .isFalse();
  }

  @Test
  void managingOneResourceGivesNoRightsOnAnother() {
    User manager = managerOf(resource);

    assertThat(
            policy.mayDelegate(manager, NOTHING_INHERITED, user("ana", Role.USER), readOn(item())))
        .isFalse();
  }

  @Test
  void aManagerMayNotChangeItsOwnPermissions() {
    User manager = managerOf(resource);

    assertThat(policy.mayDelegate(manager, NOTHING_INHERITED, manager, readOn(resource))).isFalse();
  }

  @Test
  void aManagerMayNotTouchAnotherManagerOrAnAdministrator() {
    User manager = managerOf(resource);
    User otherManager = managerOf(resource);
    User admin = user("root", Role.ADMIN);

    assertThat(policy.mayDelegate(manager, NOTHING_INHERITED, otherManager, readOn(resource)))
        .isFalse();
    assertThat(policy.mayDelegate(manager, NOTHING_INHERITED, admin, readOn(resource))).isFalse();
  }

  @Test
  void aDeactivatedGranterMayNotDelegateWhateverItsRole() {
    User admin = user("root", Role.ADMIN);
    admin.deactivate();
    User manager = managerOf(resource);
    manager.deactivate();
    User target = user("ana", Role.USER);

    assertThat(policy.mayDelegate(admin, NOTHING_INHERITED, target, readOn(resource))).isFalse();
    assertThat(
            policy.mayDelegate(
                manager, Optional.of(PermissionLevel.MANAGE), target, readOn(resource)))
        .isFalse();
  }

  @Test
  void aGuestNeverDelegatesEvenWithManageFromItsLink() {
    User guest = User.createGuest(resource, PermissionLevel.MANAGE);

    assertThat(
            policy.mayDelegate(guest, NOTHING_INHERITED, user("ana", Role.USER), readOn(resource)))
        .isFalse();
  }

  private static Permission readOn(ItemId resource) {
    return new Permission(resource, PermissionLevel.READ);
  }

  private static Permission writeOn(ItemId resource) {
    return new Permission(resource, PermissionLevel.WRITE);
  }

  // ------------------------------------------------------------------ through collections

  private static CatalogSnapshot catalogWith(CollectionSnapshot... collections) {
    return new CatalogSnapshot(
        new CatalogId(UUID.randomUUID()), "library", 1, List.of(), List.of(collections));
  }

  private static CollectionSnapshot collection(ItemId id, ItemId... items) {
    return new CollectionSnapshot(id, "collection", "", List.of(items));
  }

  @Test
  void aPermissionOnACollectionReachesWhatItContainsAtAnyDepth() {
    ItemId outer = item();
    ItemId inner = item();
    CatalogSnapshot catalog = catalogWith(collection(outer, inner), collection(inner, resource));
    User user = user("ana", Role.USER);
    user.grant(new Permission(outer, PermissionLevel.WRITE));

    assertThat(policy.effectiveLevel(user, resource, catalog)).contains(PermissionLevel.WRITE);
    assertThat(policy.authorizes(user, resource, AccessType.MODIFY, catalog)).isTrue();
    assertThat(policy.authorizes(user, resource, AccessType.DELETE, catalog)).isFalse();
    assertThat(policy.authorizes(user, resource, AccessType.VIEW)).isFalse();
  }

  @Test
  void theHighestLevelOnTheWayUpWins() {
    ItemId shelf = item();
    CatalogSnapshot catalog = catalogWith(collection(shelf, resource));
    User user = user("ana", Role.USER);
    user.grant(new Permission(resource, PermissionLevel.MANAGE));
    user.grant(new Permission(shelf, PermissionLevel.READ));

    assertThat(policy.effectiveLevel(user, resource, catalog)).contains(PermissionLevel.MANAGE);
    assertThat(policy.effectiveLevel(user, shelf, catalog)).contains(PermissionLevel.READ);
  }

  @Test
  void aCollectionThatDoesNotContainTheResourceGivesNothing() {
    ItemId other = item();
    CatalogSnapshot catalog = catalogWith(collection(other, item()));
    User user = user("ana", Role.USER);
    user.grant(new Permission(other, PermissionLevel.MANAGE));

    assertThat(policy.effectiveLevel(user, resource, catalog)).isEmpty();
    assertThat(policy.authorizes(user, resource, AccessType.VIEW, catalog)).isFalse();
  }

  @Test
  void administratorsAndDeactivatedUsersAreTheSameWithACatalog() {
    ItemId shelf = item();
    CatalogSnapshot catalog = catalogWith(collection(shelf, resource));
    User admin = user("root", Role.ADMIN);
    User gone = user("ana", Role.USER);
    gone.grant(new Permission(shelf, PermissionLevel.MANAGE));
    gone.deactivate();

    assertThat(policy.authorizes(admin, resource, AccessType.DELETE, catalog)).isTrue();
    assertThat(policy.effectiveLevel(gone, resource, catalog)).isEmpty();
  }

  @Test
  void aStoredCycleDoesNotLoopForever() {
    ItemId a = item();
    ItemId b = item();
    CatalogSnapshot catalog = catalogWith(collection(a, b), collection(b, a, resource));
    User user = user("ana", Role.USER);
    user.grant(new Permission(a, PermissionLevel.READ));

    assertThat(policy.effectiveLevel(user, resource, catalog)).contains(PermissionLevel.READ);
  }
}
