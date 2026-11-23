package co.omnistream.domain.service;

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
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.sharing.SharedLink;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.dto.AccessGrant;
import co.omnistream.domain.port.dto.Credentials;
import co.omnistream.domain.port.dto.SessionToken;
import co.omnistream.domain.port.in.IAccessControl;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.ICredentialRegistry;
import co.omnistream.domain.port.out.IIdentityProvider;
import co.omnistream.domain.port.out.ISharedLinkRepository;
import co.omnistream.domain.port.out.ITokenRepository;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.snapshot.SharedLinkSnapshot;
import co.omnistream.domain.snapshot.UserSnapshot;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Domain service that implements the IAccessControl driving port.
 *
 * <p>Acts as the context of the identity verification strategy (Strategy pattern over {@link
 * IIdentityProvider}).
 */
public final class AccessService extends AuthorizedService implements IAccessControl {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;

  private final List<IIdentityProvider> providers;
  private final ICredentialRegistry credentialRegistry;
  private final ITokenRepository tokenRepository;
  private final ISharedLinkRepository sharedLinkRepository;
  private final ICatalogRepository catalogRepository;
  private final IUnitOfWork unitOfWork;
  private final Duration tokenLifetime;

  public AccessService(
      IUserRepository userRepository,
      AccessPolicy accessPolicy,
      Clock clock,
      List<IIdentityProvider> providers,
      ICredentialRegistry credentialRegistry,
      ITokenRepository tokenRepository,
      ISharedLinkRepository sharedLinkRepository,
      ICatalogRepository catalogRepository,
      IUnitOfWork unitOfWork,
      Duration tokenLifetime) {
    super(userRepository, accessPolicy, clock);
    this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
    this.credentialRegistry = Objects.requireNonNull(credentialRegistry, "credentialRegistry");
    this.tokenRepository = Objects.requireNonNull(tokenRepository, "tokenRepository");
    this.sharedLinkRepository =
        Objects.requireNonNull(sharedLinkRepository, "sharedLinkRepository");
    this.catalogRepository = Objects.requireNonNull(catalogRepository, "catalogRepository");
    this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    if (!Objects.requireNonNull(tokenLifetime, "tokenLifetime").isPositive()) {
      throw new IllegalArgumentException("tokenLifetime must be positive");
    }
    this.tokenLifetime = tokenLifetime;
  }

  @Override
  public SessionToken authenticateUser(Credentials c) {
    IIdentityProvider provider = selectProvider(c);
    LinkedIdentity identity =
        provider.verify(c).orElseThrow(() -> new InvalidCredentials("Invalid credentials"));
    User user =
        userRepository
            .findByIdentity(identity)
            .orElseThrow(() -> new InvalidCredentials("Invalid credentials"));
    if (!user.toSnapshot().active()) {
      throw new AccessDenied("User account is deactivated");
    }
    return issueToken(user);
  }

  @Override
  public Optional<SessionToken> validateToken(String value) {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      return Optional.empty();
    }
    Instant now = clock.instant();
    return tokenRepository
        .find(value)
        .filter(token -> token.isValidAt(now))
        .filter(token ->
            userRepository
                .findById(token.userId())
                .map(user -> user.toSnapshot().active())
                .orElse(false));
  }

  @Override
  public void logout(String value) {
    Objects.requireNonNull(value, "value");
    tokenRepository.delete(value);
  }

  /**
   * Finds the link by its token ({@code ResourceNotFound} if there is none) and throws {@code
   * ExpiredLink} if {@code !link.isValidAt(clock.instant())}, which also covers revoked links. The
   * link itself only answers whether it is valid. If it is, issue a token for its guest user, so
   * that from then on the guest is authorized like anyone else.
   */
  @Override
  public SessionToken accessWithLink(String link) {
    Objects.requireNonNull(link, "link");
    SharedLink sharedLink =
        sharedLinkRepository
            .findByToken(link)
            .orElseThrow(() -> new ResourceNotFound("Shared link not found"));
    if (!sharedLink.isValidAt(clock.instant())) {
      throw new ExpiredLink("Shared link has expired or been revoked");
    }
    User guest = loadUser(sharedLink.toSnapshot().guestId());
    if (!guest.toSnapshot().active()) {
      throw new AccessDenied("Guest account is deactivated");
    }
    return issueToken(guest);
  }

  @Override
  public boolean authorizeResource(UserId u, ItemId r, AccessType acc) {
    Objects.requireNonNull(r, "r");
    Objects.requireNonNull(acc, "acc");
    User user = loadUser(u);
    return accessPolicy.authorizes(user, r, acc, catalogRepository.getCatalog().toSnapshot());
  }

  /**
   * Nobody shares with more rights than they have: if {@code inheritedLevel(u, col)} is empty or
   * below {@code level}, throw {@code AccessDenied}. Then, in one unit of work: {@code
   * User.createGuest(col, level)} and save it, {@code SharedLink.create(col, <guest id>, level, u,
   * lifetime, clock.instant())} and save it too.
   */
  @Override
  public SharedLinkSnapshot shareCollection(
      UserId u, ItemId col, PermissionLevel level, Duration lifetime) {
    Objects.requireNonNull(col, "col");
    Objects.requireNonNull(level, "level");
    if (!Objects.requireNonNull(lifetime, "lifetime").isPositive()) {
      throw new IllegalArgumentException("lifetime must be positive");
    }
    User sharer = loadUser(u);
    Optional<PermissionLevel> allowed = inheritedLevel(sharer, col);
    if (allowed.isEmpty() || allowed.get().compareTo(level) < 0) {
      throw new AccessDenied("Cannot share collection with level " + level);
    }
    return unitOfWork.execute(() -> {
      User guest = User.createGuest(col, level);
      userRepository.save(guest);
      SharedLink link =
          SharedLink.create(col, guest.toSnapshot().id(), level, u, lifetime, clock.instant());
      sharedLinkRepository.save(link);
      return link.toSnapshot();
    });
  }

  /**
   * The password goes to {@link ICredentialRegistry#register}, which returns the local identity;
   * the user is created with that identity and never sees the password.
   */
  @Override
  public UserId createUser(UserId admin, String username, String password, Role role) {
    requireAdmin(admin);
    String cleanUsername = requireNotBlank(username, "username");
    requireNotBlank(password, "password");
    Objects.requireNonNull(role, "role");
    if (role == Role.GUEST) {
      throw new AccessDenied("Guests can only be created from shared links");
    }
    if (userRepository.findByUsername(cleanUsername).isPresent()) {
      throw new IllegalStateException("Username already exists: " + cleanUsername);
    }
    return unitOfWork.execute(() -> {
      LinkedIdentity identity = credentialRegistry.register(cleanUsername, password);
      User created = User.create(cleanUsername, role, identity);
      userRepository.save(created);
      return created.toSnapshot().id();
    });
  }

  /**
   * {@code User.changeRole} throws {@code IllegalArgumentException} for {@code Role.GUEST} and
   * {@code IllegalStateException} if the target is a guest. Both can come from the request, so
   * check them here and answer with a business error before calling it. The same goes for granting
   * or revoking permissions of a guest.
   */
  @Override
  public void manageRoles(UserId admin, UserId target, Role role) {
    requireAdmin(admin);
    Objects.requireNonNull(role, "role");
    if (role == Role.GUEST) {
      throw new AccessDenied("Cannot assign guest role");
    }
    User user = loadUser(target);
    if (user.toSnapshot().role() == Role.GUEST) {
      throw new AccessDenied("Cannot change a guest's role");
    }
    user.changeRole(role);
    userRepository.save(user);
  }

  /**
   * Delegation (ADR 0024): load both users, and throw {@code AccessDenied} unless {@code
   * accessPolicy.mayDelegate(granter, inheritedLevel(granter, p.resource()), target, p)}. Then
   * {@code target.grant(p)} and save it. A guest target makes {@code grant} throw {@code
   * IllegalStateException}; see {@link #manageRoles} for answering that with a business error.
   */
  @Override
  public void grantPermission(UserId granter, UserId u, Permission p) {
    delegatePermission(granter, u, p, user -> user.grant(p));
  }

  /**
   * Same rule as {@link #grantPermission}, asked about the permission being taken away: who may
   * give it may take it back.
   */
  @Override
  public void revokePermission(UserId granter, UserId u, Permission p) {
    delegatePermission(granter, u, p, user -> user.revoke(p));
  }

  @Override
  public void deactivateUser(UserId admin, UserId u) {
    requireAdmin(admin);
    User user = loadUser(u);
    user.deactivate();
    userRepository.save(user);
  }

  /**
   * For the administration screen: {@code requireAdmin(admin)}, then {@code
   * userRepository.findAll()} without the guests (they belong to their shared links) and without
   * the system account (its identity is issued by {@code LinkedIdentity.SYSTEM_ISSUER}), as
   * snapshots sorted by username.
   */
  @Override
  public List<UserSnapshot> listUsers(UserId admin) {
    requireAdmin(admin);
    return userRepository.findAll().stream()
        .map(User::toSnapshot)
        .filter(snapshot -> snapshot.role() != Role.GUEST)
        .filter(snapshot ->
            snapshot.identities().stream()
                .noneMatch(id -> LinkedIdentity.SYSTEM_ISSUER.equals(id.issuer())))
        .filter(snapshot ->
            snapshot.identities().stream()
                .noneMatch(id -> LinkedIdentity.SYSTEM_ISSUER.equals(id.issuer())))
        .sorted(Comparator.comparing(UserSnapshot::username))
        .toList();
  }

  /**
   * Lets whoever manages something find the person to share it with, without listing the users.
   * {@code u} must be an administrator or hold {@code MANAGE} on at least one resource, otherwise
   * {@code AccessDenied}. Answers {@code ResourceNotFound} if there is no active, non-guest user
   * with that username.
   */
  @Override
  public UserId findUser(UserId u, String username) {
    String cleanUsername = requireNotBlank(username, "username");
    User caller = loadUser(u);
    if (!canFindUsers(caller)) {
      throw new AccessDenied("Only administrators or resource managers can find users");
    }
    return userRepository
        .findByUsername(cleanUsername)
        .map(User::toSnapshot)
        .filter(snapshot -> snapshot.active() && snapshot.role() != Role.GUEST)
        .map(UserSnapshot::id)
        .orElseThrow(() -> new ResourceNotFound("Active user not found: " + cleanUsername));
  }

  /**
   * Who has a permission on the resource itself (not through a collection that contains it): the
   * access list that its managers see. {@code u} must reach {@code MANAGE} on it, counting {@link
   * #inheritedLevel}, otherwise {@code AccessDenied}. From {@code userRepository.findAll()}: the
   * active, non-guest users with a permission that {@code appliesTo(resource)}, sorted by username.
   * Administrators are left out unless they have one: they reach everything anyway.
   */
  @Override
  public List<AccessGrant> accessTo(UserId u, ItemId resource) {
    Objects.requireNonNull(resource, "resource");
    User caller = loadUser(u);
    if (caller.toSnapshot().role() == Role.GUEST
        || inheritedLevel(caller, resource)
            .filter(level -> level == PermissionLevel.MANAGE)
            .isEmpty()) {
      throw new AccessDenied("Cannot view access list for " + resource.value());
    }
    return userRepository.findAll().stream()
        .map(User::toSnapshot)
        .filter(snapshot -> snapshot.active() && snapshot.role() != Role.GUEST)
        .flatMap(snapshot ->
            snapshot.permissions().stream()
                .filter(permission -> permission.appliesTo(resource))
                .findFirst()
                .map(permission ->
                    new AccessGrant(snapshot.id(), snapshot.username(), permission.level()))
                .stream())
        .sorted(Comparator.comparing(AccessGrant::username))
        .toList();
  }

  private void delegatePermission(
      UserId granter, UserId targetId, Permission permission, Consumer<User> action) {
    Objects.requireNonNull(permission, "p");
    User granterUser = loadUser(granter);
    User targetUser = loadUser(targetId);
    Optional<PermissionLevel> inherited = inheritedLevel(granterUser, permission.resource());
    if (!accessPolicy.mayDelegate(granterUser, inherited, targetUser, permission)) {
      throw new AccessDenied(
          "Not allowed to change permission on " + permission.resource().value());
    }
    if (targetUser.toSnapshot().role() == Role.GUEST) {
      throw new AccessDenied("Cannot change a guest's permissions");
    }
    action.accept(targetUser);
    userRepository.save(targetUser);
  }

  private static boolean canFindUsers(User caller) {
    if (caller.isAdmin()) {
      return true;
    }
    UserSnapshot snapshot = caller.toSnapshot();
    return snapshot.active()
        && snapshot.role() != Role.GUEST
        && snapshot.permissions().stream()
            .anyMatch(permission -> permission.level() == PermissionLevel.MANAGE);
  }

  private static String requireNotBlank(String value, String name) {
    String stripped = Objects.requireNonNull(value, name).strip();
    if (stripped.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return stripped;
  }

  /**
   * The level {@code u} reaches on {@code r} through the collections that contain it: {@code
   * accessPolicy.effectiveLevel(u, r, catalogRepository.getCatalog().toSnapshot())}. It also counts
   * {@code r} itself, which {@code mayDelegate} adds anyway.
   */
  private Optional<PermissionLevel> inheritedLevel(User u, ItemId r) {
    return accessPolicy.effectiveLevel(u, r, catalogRepository.getCatalog().toSnapshot());
  }

  private IIdentityProvider selectProvider(Credentials c) {
    Objects.requireNonNull(c, "c");
    return providers.stream()
        .filter(provider -> provider.supports(c))
        .findFirst()
        .orElseThrow(() -> new InvalidCredentials("No identity provider supports " + c.provider()));
  }

  private SessionToken issueToken(User u) {
    UserSnapshot snapshot = u.toSnapshot();
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    SessionToken token =
        new SessionToken(
            value, snapshot.id(), snapshot.role(), clock.instant().plus(tokenLifetime));
    tokenRepository.save(token);
    return token;
  }
}
