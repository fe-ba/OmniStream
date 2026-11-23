package co.omnistream.domain.service;

import static co.omnistream.domain.model.access.AccessFixtures.item;
import static co.omnistream.domain.model.catalog.CatalogFixtures.video;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.ExpiredLink;
import co.omnistream.domain.exception.InvalidCredentials;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.Collection;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaFile;
import co.omnistream.domain.model.sharing.SharedLink;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.dto.AccessGrant;
import co.omnistream.domain.port.dto.Credentials;
import co.omnistream.domain.port.dto.LocalCredentials;
import co.omnistream.domain.port.dto.SessionToken;
import co.omnistream.domain.port.out.IIdentityProvider;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.port.out.memory.InMemoryCatalogRepository;
import co.omnistream.domain.port.out.memory.InMemoryCredentialRegistry;
import co.omnistream.domain.port.out.memory.InMemorySharedLinkRepository;
import co.omnistream.domain.port.out.memory.InMemoryTokenRepository;
import co.omnistream.domain.port.out.memory.InMemoryUnitOfWork;
import co.omnistream.domain.port.out.memory.InMemoryUserRepository;
import co.omnistream.domain.snapshot.SharedLinkSnapshot;
import co.omnistream.domain.snapshot.UserSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccessServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-21T18:00:00Z");
  private static final Duration TOKEN_LIFETIME = Duration.ofHours(12);

  private final MutableClock clock = new MutableClock(NOW);
  private final InMemoryUserRepository users = new InMemoryUserRepository();
  private final InMemoryTokenRepository tokens = new InMemoryTokenRepository(clock);
  private final InMemorySharedLinkRepository links = new InMemorySharedLinkRepository();
  private final InMemoryCredentialRegistry credentials = new InMemoryCredentialRegistry();
  private final IIdentityProvider localProvider = new LocalCredentialVerifier(credentials);
  private final InMemoryCatalogRepository catalogRepository = new InMemoryCatalogRepository();
  private final CountingUnitOfWork unitOfWork = new CountingUnitOfWork();

  private AccessService service;
  private UserId adminId;

  @BeforeEach
  void setUp() {
    service =
        new AccessService(
            users,
            new AccessPolicy(),
            clock,
            List.of(localProvider),
            credentials,
            tokens,
            links,
            catalogRepository,
            unitOfWork,
            TOKEN_LIFETIME);
    LinkedIdentity adminIdentity = credentials.register("root", "secret");
    User admin = User.create("root", Role.ADMIN, adminIdentity);
    users.save(admin);
    adminId = admin.toSnapshot().id();
  }

  // ---------------------------------------------------------------- authentication

  @Test
  void authenticatesLocalUserAndIssuesTokenWithConfiguredLifetime() {
    SessionToken token = service.authenticateUser(new LocalCredentials("local", "root", "secret"));

    assertThat(token.userId()).isEqualTo(adminId);
    assertThat(token.role()).isEqualTo(Role.ADMIN);
    assertThat(token.expiresAt()).isEqualTo(NOW.plus(TOKEN_LIFETIME));
    assertThat(token.value()).isNotBlank();
    assertThat(tokens.find(token.value())).contains(token);
  }

  @Test
  void rejectsInvalidPasswordOrUnknownUser() {
    assertThatThrownBy(
            () -> service.authenticateUser(new LocalCredentials("local", "root", "wrong")))
        .isInstanceOf(InvalidCredentials.class);
    assertThatThrownBy(
            () -> service.authenticateUser(new LocalCredentials("local", "missing", "secret")))
        .isInstanceOf(InvalidCredentials.class);
  }

  @Test
  void rejectsUnsupportedProviderCredentials() {
    Credentials unsupported = new UnsupportedCredentials();

    assertThatThrownBy(() -> service.authenticateUser(unsupported))
        .isInstanceOf(InvalidCredentials.class);
  }

  @Test
  void rejectsDeactivatedUserOnLogin() {
    UserId anaId = service.createUser(adminId, "ana", "pass-123", Role.USER);
    service.deactivateUser(adminId, anaId);

    assertThatThrownBy(
            () -> service.authenticateUser(new LocalCredentials("local", "ana", "pass-123")))
        .isInstanceOf(AccessDenied.class);
  }

  @Test
  void usesFirstIdentityProviderThatSupportsCredentials() {
    LinkedIdentity oidcIdentity = new LinkedIdentity("https://idp.example", "sub-42");
    User oidcUser = User.create("maria", Role.USER, oidcIdentity);
    users.save(oidcUser);
    IIdentityProvider secondProvider =
        new IIdentityProvider() {
          @Override
          public boolean supports(Credentials c) {
            return c instanceof UnsupportedCredentials;
          }

          @Override
          public Optional<LinkedIdentity> verify(Credentials c) {
            return Optional.of(oidcIdentity);
          }
        };
    AccessService multiProviderService =
        new AccessService(
            users,
            new AccessPolicy(),
            clock,
            List.of(localProvider, secondProvider),
            credentials,
            tokens,
            links,
            catalogRepository,
            unitOfWork,
            TOKEN_LIFETIME);

    SessionToken token = multiProviderService.authenticateUser(new UnsupportedCredentials());

    assertThat(token.userId()).isEqualTo(oidcUser.toSnapshot().id());
  }

  // ---------------------------------------------------------------- tokens and logout

  @Test
  void validatesTokenBeforeExpiryAndRejectsAfterExpiry() {
    SessionToken token = service.authenticateUser(new LocalCredentials("local", "root", "secret"));

    assertThat(service.validateToken(token.value())).contains(token);

    clock.set(NOW.plus(TOKEN_LIFETIME).minusSeconds(1));
    assertThat(service.validateToken(token.value())).contains(token);

    clock.set(NOW.plus(TOKEN_LIFETIME));

    assertThat(service.validateToken(token.value())).isEmpty();
  }

  @Test
  void deactivatingAUserInvalidatesTheirExistingTokenImmediately() {
    UserId anaId = service.createUser(adminId, "ana", "pass-123", Role.USER);
    SessionToken token = service.authenticateUser(new LocalCredentials("local", "ana", "pass-123"));

    service.deactivateUser(adminId, anaId);

    assertThat(service.validateToken(token.value())).isEmpty();
  }

  @Test
  void blankTokenReturnsEmptyWithoutCheckingStore() {
    assertThat(service.validateToken("   ")).isEmpty();
  }

  @Test
  void logoutRemovesTheTokenAndIsIdempotent() {
    SessionToken token = service.authenticateUser(new LocalCredentials("local", "root", "secret"));

    service.logout(token.value());
    service.logout(token.value());

    assertThat(tokens.find(token.value())).isEmpty();
  }

  // ---------------------------------------------------------------- authorization and shared links

  @Test
  void authorizeResourceResolvesInheritedPermissionsThroughCatalogCollections() {
    CatalogIndex catalog = catalogRepository.getCatalog();
    MediaFile clip = video("clip", "/library/clip.mp4", 'a');
    catalog.ingest(clip, NOW);
    Collection folder = catalog.createCollection("Docs", NOW);
    ItemId folderId = folder.toSnapshot().id();
    ItemId clipId = clip.toSnapshot().id();
    catalog.addToCollection(folderId, clipId, NOW);
    catalogRepository.save(catalog);

    UserId anaId = service.createUser(adminId, "ana", "pass-123", Role.USER);
    service.grantPermission(adminId, anaId, new Permission(folderId, PermissionLevel.READ));

    assertThat(service.authorizeResource(anaId, clipId, AccessType.VIEW)).isTrue();
    assertThat(service.authorizeResource(anaId, clipId, AccessType.MODIFY)).isFalse();
    assertThat(service.authorizeResource(adminId, clipId, AccessType.DELETE)).isTrue();
  }

  @Test
  void shareCollectionCreatesGuestUserAndLinkInsideOneUnitOfWork() {
    CatalogIndex catalog = catalogRepository.getCatalog();
    Collection trips = catalog.createCollection("Trips", NOW);
    ItemId tripsId = trips.toSnapshot().id();
    catalogRepository.save(catalog);

    UserId anaId = service.createUser(adminId, "ana", "pass-123", Role.USER);
    service.grantPermission(adminId, anaId, new Permission(tripsId, PermissionLevel.WRITE));
    int beforeExecutions = unitOfWork.executions;

    SharedLinkSnapshot link =
        service.shareCollection(anaId, tripsId, PermissionLevel.READ, Duration.ofDays(7));

    assertThat(unitOfWork.executions).isEqualTo(beforeExecutions + 1);
    assertThat(link.collectionId()).isEqualTo(tripsId);
    assertThat(link.level()).isEqualTo(PermissionLevel.READ);
    assertThat(link.createdBy()).isEqualTo(anaId);
    assertThat(link.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    assertThat(users.findById(link.guestId()))
        .hasValueSatisfying(guest -> {
          assertThat(guest.toSnapshot().role()).isEqualTo(Role.GUEST);
          assertThat(guest.canAccess(tripsId, AccessType.VIEW)).isTrue();
          assertThat(guest.canAccess(tripsId, AccessType.MODIFY)).isFalse();
        });
  }

  @Test
  void cannotShareCollectionWithMoreRightsThanCallerHolds() {
    ItemId collectionId = item();
    UserId anaId = service.createUser(adminId, "ana", "pass-123", Role.USER);
    service.grantPermission(adminId, anaId, new Permission(collectionId, PermissionLevel.READ));

    assertThatThrownBy(() ->
            service.shareCollection(anaId, collectionId, PermissionLevel.WRITE, Duration.ofDays(1)))
        .isInstanceOf(AccessDenied.class);
    assertThatThrownBy(
            () -> service.shareCollection(anaId, item(), PermissionLevel.READ, Duration.ofDays(1)))
        .isInstanceOf(AccessDenied.class);
    int usersBeforeInvalidLifetime = users.findAll().size();
    assertThatThrownBy(
            () -> service.shareCollection(anaId, collectionId, PermissionLevel.READ, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(users.findAll()).hasSize(usersBeforeInvalidLifetime);
  }

  @Test
  void accessWithLinkIssuesTokenForValidLinkAndRejectsUnknownExpiredRevokedOrDeactivatedGuest() {
    ItemId collectionId = item();
    SharedLinkSnapshot link =
        service.shareCollection(adminId, collectionId, PermissionLevel.READ, Duration.ofHours(2));

    SessionToken guestToken = service.accessWithLink(link.token());

    assertThat(guestToken.userId()).isEqualTo(link.guestId());
    assertThat(guestToken.role()).isEqualTo(Role.GUEST);
    assertThatThrownBy(() -> service.accessWithLink("unknown-token"))
        .isInstanceOf(ResourceNotFound.class);

    SharedLink stored = links.findByToken(link.token()).orElseThrow();
    stored.revoke();
    links.save(stored);
    assertThatThrownBy(() -> service.accessWithLink(link.token())).isInstanceOf(ExpiredLink.class);

    SharedLinkSnapshot second =
        service.shareCollection(
            adminId, collectionId, PermissionLevel.READ, Duration.ofMinutes(30));
    clock.set(NOW.plus(Duration.ofMinutes(30)));
    assertThatThrownBy(() -> service.accessWithLink(second.token()))
        .isInstanceOf(ExpiredLink.class);

    SharedLinkSnapshot third =
        service.shareCollection(
            adminId, collectionId, PermissionLevel.READ, Duration.ofMinutes(30));
    service.deactivateUser(adminId, third.guestId());
    assertThatThrownBy(() -> service.accessWithLink(third.token()))
        .isInstanceOf(AccessDenied.class);
  }

  // ---------------------------------------------------------------- user and permission management

  @Test
  void createUserRegistersCredentialsAndUserInUnitOfWorkForAdminsOnly() {
    int beforeExecutions = unitOfWork.executions;

    UserId anaId = service.createUser(adminId, " ana ", "pass-123", Role.USER);
    SessionToken token = service.authenticateUser(new LocalCredentials("local", "ana", "pass-123"));

    assertThat(unitOfWork.executions).isEqualTo(beforeExecutions + 1);
    assertThat(unitOfWork.inside()).isFalse();
    assertThat(token.userId()).isEqualTo(anaId);
    assertThatThrownBy(() -> service.createUser(anaId, "luis", "pass-123", Role.USER))
        .isInstanceOf(AccessDenied.class);
    assertThatThrownBy(() -> service.createUser(adminId, "ana", "other-pass", Role.USER))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> service.createUser(adminId, "guest", "pass-123", Role.GUEST))
        .isInstanceOf(AccessDenied.class);
    assertThatThrownBy(() -> service.createUser(adminId, "luis", "   ", Role.USER))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void manageRolesAllowsOnlyAdminsAndBlocksGuestTransitions() {
    UserId anaId = service.createUser(adminId, "ana", "pass-123", Role.USER);
    SharedLinkSnapshot link =
        service.shareCollection(adminId, item(), PermissionLevel.READ, Duration.ofHours(1));

    service.manageRoles(adminId, anaId, Role.ADMIN);
    assertThat(users.findById(anaId).orElseThrow().isAdmin()).isTrue();

    service.manageRoles(adminId, anaId, Role.USER);
    assertThat(users.findById(anaId).orElseThrow().isAdmin()).isFalse();

    assertThatThrownBy(() -> service.manageRoles(anaId, anaId, Role.ADMIN))
        .isInstanceOf(AccessDenied.class);
    assertThatThrownBy(() -> service.manageRoles(adminId, anaId, Role.GUEST))
        .isInstanceOf(AccessDenied.class);
    assertThatThrownBy(() -> service.manageRoles(adminId, link.guestId(), Role.USER))
        .isInstanceOf(AccessDenied.class);
  }

  @Test
  void resourceManagerCanDelegateReadAndWriteButCannotTouchManageOrGuests() {
    CatalogIndex catalog = catalogRepository.getCatalog();
    MediaFile clip = video("clip", "/library/clip.mp4", 'a');
    catalog.ingest(clip, NOW);
    Collection folder = catalog.createCollection("Projects", NOW);
    ItemId folderId = folder.toSnapshot().id();
    ItemId clipId = clip.toSnapshot().id();
    catalog.addToCollection(folderId, clipId, NOW);
    catalogRepository.save(catalog);

    UserId managerId = service.createUser(adminId, "manager", "pass", Role.USER);
    UserId luisId = service.createUser(adminId, "luis", "pass", Role.USER);
    service.grantPermission(adminId, managerId, new Permission(folderId, PermissionLevel.MANAGE));

    service.grantPermission(managerId, luisId, new Permission(clipId, PermissionLevel.WRITE));
    assertThat(service.authorizeResource(luisId, clipId, AccessType.MODIFY)).isTrue();

    service.revokePermission(managerId, luisId, new Permission(clipId, PermissionLevel.WRITE));
    assertThat(service.authorizeResource(luisId, clipId, AccessType.MODIFY)).isFalse();

    assertThatThrownBy(() ->
            service.grantPermission(
                managerId, luisId, new Permission(clipId, PermissionLevel.MANAGE)))
        .isInstanceOf(AccessDenied.class);

    SharedLinkSnapshot link =
        service.shareCollection(adminId, folderId, PermissionLevel.READ, Duration.ofHours(1));
    assertThatThrownBy(() ->
            service.grantPermission(
                adminId, link.guestId(), new Permission(clipId, PermissionLevel.READ)))
        .isInstanceOf(AccessDenied.class);
  }

  @Test
  void listsUsersFindsTargetsAndListsAccessGrantsAccordingToRole() {
    ItemId folderId = item();
    UserId zoeId = service.createUser(adminId, "zoe", "pass", Role.USER);
    UserId anaId = service.createUser(adminId, "ana", "pass", Role.USER);
    UserId bobId = service.createUser(adminId, "bob", "pass", Role.USER);
    service.shareCollection(adminId, folderId, PermissionLevel.READ, Duration.ofHours(1));
    users.save(
        User.create(
            "omnistream-system",
            Role.ADMIN,
            new LinkedIdentity(LinkedIdentity.SYSTEM_ISSUER, "omnistream")));

    service.grantPermission(adminId, anaId, new Permission(folderId, PermissionLevel.MANAGE));
    service.grantPermission(adminId, zoeId, new Permission(folderId, PermissionLevel.READ));
    service.grantPermission(adminId, bobId, new Permission(folderId, PermissionLevel.WRITE));
    service.deactivateUser(adminId, bobId);

    List<UserSnapshot> listed = service.listUsers(adminId);
    assertThat(listed)
        .extracting(UserSnapshot::username)
        .containsExactly("ana", "bob", "root", "zoe");
    assertThatThrownBy(() -> service.listUsers(anaId)).isInstanceOf(AccessDenied.class);

    assertThat(service.findUser(anaId, " zoe ")).isEqualTo(zoeId);
    assertThatThrownBy(() -> service.findUser(anaId, "bob")).isInstanceOf(ResourceNotFound.class);
    assertThatThrownBy(() -> service.findUser(zoeId, "ana")).isInstanceOf(AccessDenied.class);

    List<AccessGrant> grants = service.accessTo(anaId, folderId);
    assertThat(grants)
        .containsExactly(
            new AccessGrant(anaId, "ana", PermissionLevel.MANAGE),
            new AccessGrant(zoeId, "zoe", PermissionLevel.READ));

    service.grantPermission(adminId, adminId, new Permission(folderId, PermissionLevel.MANAGE));
    assertThat(service.accessTo(anaId, folderId))
        .containsExactly(
            new AccessGrant(anaId, "ana", PermissionLevel.MANAGE),
            new AccessGrant(adminId, "root", PermissionLevel.MANAGE),
            new AccessGrant(zoeId, "zoe", PermissionLevel.READ));
    assertThatThrownBy(() -> service.accessTo(zoeId, folderId)).isInstanceOf(AccessDenied.class);
  }

  private static final class UnsupportedCredentials extends Credentials {
    UnsupportedCredentials() {
      super("oidc");
    }
  }

  private static final class LocalCredentialVerifier implements IIdentityProvider {
    private final InMemoryCredentialRegistry credentials;

    LocalCredentialVerifier(InMemoryCredentialRegistry credentials) {
      this.credentials = credentials;
    }

    @Override
    public boolean supports(Credentials c) {
      return c != null && LinkedIdentity.LOCAL_ISSUER.equals(c.provider());
    }

    @Override
    public Optional<LinkedIdentity> verify(Credentials c) {
      if (!(c instanceof LocalCredentials local) || !supports(c)) {
        return Optional.empty();
      }
      String username = local.username().strip();
      String expected = credentials.passwords().get(username);
      if (expected == null || !expected.equals(local.password())) {
        return Optional.empty();
      }
      return Optional.of(new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, username));
    }
  }

  private static final class CountingUnitOfWork implements IUnitOfWork {
    private final InMemoryUnitOfWork delegate = new InMemoryUnitOfWork();
    int executions;

    @Override
    public <T> T execute(Supplier<T> work) {
      executions++;
      return delegate.execute(work);
    }

    boolean inside() {
      return delegate.inside();
    }
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    MutableClock(Instant initial) {
      this.instant = initial;
    }

    void set(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
