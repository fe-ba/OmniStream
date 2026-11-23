package co.omnistream.domain.service;

import static co.omnistream.domain.model.access.AccessFixtures.item;
import static co.omnistream.domain.model.access.AccessFixtures.user;
import static co.omnistream.domain.model.catalog.CatalogFixtures.video;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.CatalogId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.Collection;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaFile;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.port.out.memory.InMemoryUserRepository;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorizedServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-21T18:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final InMemoryUserRepository users = new InMemoryUserRepository();
  private final TestAuthorizedService service =
      new TestAuthorizedService(users, new AccessPolicy(), CLOCK);

  @Test
  void loadsAnExistingUserById() {
    User ana = user("ana", Role.USER);
    users.save(ana);

    User loaded = service.exposeLoadUser(ana.toSnapshot().id());

    assertThat(loaded).isEqualTo(ana);
  }

  @Test
  void loadingAnUnknownUserThrowsResourceNotFound() {
    UserId unknown = new UserId(UUID.randomUUID());

    assertThatThrownBy(() -> service.exposeLoadUser(unknown)).isInstanceOf(ResourceNotFound.class);
  }

  @Test
  void checkAccessAllowsWhenPolicyAuthorizesAndRejectsOtherwise() {
    User ana = user("ana", Role.USER);
    ItemId fileId = item();
    ana.grant(new Permission(fileId, PermissionLevel.READ));
    users.save(ana);
    UserId anaId = ana.toSnapshot().id();

    assertThatCode(() -> service.exposeCheckAccess(anaId, fileId, AccessType.VIEW))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> service.exposeCheckAccess(anaId, fileId, AccessType.MODIFY))
        .isInstanceOf(AccessDenied.class);
  }

  @Test
  void checkAccessWithCatalogResolvesPermissionsInheritedFromNestedCollections() {
    CatalogIndex catalog =
        CatalogIndex.fromSnapshot(
            new CatalogSnapshot(
                new CatalogId(UUID.randomUUID()), "OmniStream", 0L, List.of(), List.of()));
    MediaFile clip = video("clip", "/library/clip.mp4", 'a');
    catalog.ingest(clip, NOW);
    Collection parent = catalog.createCollection("Trips", NOW);
    Collection child = catalog.createCollection("Summer", NOW);
    ItemId clipId = clip.toSnapshot().id();
    ItemId parentId = parent.toSnapshot().id();
    ItemId childId = child.toSnapshot().id();
    catalog.addToCollection(childId, clipId, NOW);
    catalog.addToCollection(parentId, childId, NOW);

    User ana = user("ana", Role.USER);
    ana.grant(new Permission(parentId, PermissionLevel.READ));
    users.save(ana);
    UserId anaId = ana.toSnapshot().id();
    CatalogSnapshot snapshot = catalog.toSnapshot();

    assertThatCode(() -> service.exposeCheckAccess(anaId, clipId, AccessType.VIEW, snapshot))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> service.exposeCheckAccess(anaId, clipId, AccessType.MODIFY, snapshot))
        .isInstanceOf(AccessDenied.class);
  }

  @Test
  void checkAccessRejectsDeactivatedUsers() {
    User ana = user("ana", Role.USER);
    ItemId fileId = item();
    ana.grant(new Permission(fileId, PermissionLevel.MANAGE));
    ana.deactivate();
    users.save(ana);

    assertThatThrownBy(
            () -> service.exposeCheckAccess(ana.toSnapshot().id(), fileId, AccessType.VIEW))
        .isInstanceOf(AccessDenied.class);
  }

  @Test
  void requireAdminAllowsActiveAdminsOnly() {
    User admin = user("root", Role.ADMIN);
    User regular = user("ana", Role.USER);
    User guest = User.createGuest(item(), PermissionLevel.MANAGE);
    User deactivatedAdmin = user("old-root", Role.ADMIN);
    deactivatedAdmin.deactivate();
    users.save(admin);
    users.save(regular);
    users.save(guest);
    users.save(deactivatedAdmin);

    assertThatCode(() -> service.exposeRequireAdmin(admin.toSnapshot().id()))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> service.exposeRequireAdmin(regular.toSnapshot().id()))
        .isInstanceOf(AccessDenied.class);
    assertThatThrownBy(() -> service.exposeRequireAdmin(guest.toSnapshot().id()))
        .isInstanceOf(AccessDenied.class);
    assertThatThrownBy(() -> service.exposeRequireAdmin(deactivatedAdmin.toSnapshot().id()))
        .isInstanceOf(AccessDenied.class);
  }

  private static final class TestAuthorizedService extends AuthorizedService {
    TestAuthorizedService(IUserRepository users, AccessPolicy policy, Clock clock) {
      super(users, policy, clock);
    }

    User exposeLoadUser(UserId u) {
      return loadUser(u);
    }

    void exposeCheckAccess(UserId u, ItemId r, AccessType acc) {
      checkAccess(u, r, acc);
    }

    void exposeCheckAccess(UserId u, ItemId r, AccessType acc, CatalogSnapshot catalog) {
      checkAccess(u, r, acc, catalog);
    }

    void exposeRequireAdmin(UserId admin) {
      requireAdmin(admin);
    }
  }
}
