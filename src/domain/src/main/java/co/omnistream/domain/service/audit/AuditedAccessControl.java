package co.omnistream.domain.service.audit;

import co.omnistream.domain.exception.InvalidCredentials;
import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.AccessGrant;
import co.omnistream.domain.port.dto.Credentials;
import co.omnistream.domain.port.dto.LocalCredentials;
import co.omnistream.domain.port.dto.SessionToken;
import co.omnistream.domain.port.in.IAccessControl;
import co.omnistream.domain.port.out.IAuditLog;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.snapshot.SharedLinkSnapshot;
import co.omnistream.domain.snapshot.UserSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Records logins, links, users and permissions in the audit trail, and passes every call on to the
 * access service (Decorator, ADR 0025).
 */
public final class AuditedAccessControl extends AuditingDecorator implements IAccessControl {
  private final IAccessControl delegate;
  private final IUserRepository userRepository;

  public AuditedAccessControl(
      IAccessControl delegate,
      IUserRepository userRepository,
      IAuditLog auditLog,
      IUnitOfWork unitOfWork,
      Clock clock) {
    super(auditLog, unitOfWork, clock);
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
  }

  /**
   * A wrong password is recorded on the account it was tried on. An unknown username is not
   * recorded: there is no account to attach it to.
   */
  @Override
  public SessionToken authenticateUser(Credentials c) {
    try {
      return audited(
          () -> delegate.authenticateUser(c),
          token -> Optional.of(entry(token.userId(), AuditAction.LOGGED_IN).saying(c.provider())));
    } catch (InvalidCredentials e) {
      if (c instanceof LocalCredentials local) {
        userRepository
            .findByUsername(local.username())
            .map(user -> user.toSnapshot().id())
            .ifPresent(id -> record(entry(id, AuditAction.LOGIN_FAILED).saying(c.provider())));
      }
      throw e;
    }
  }

  @Override
  public Optional<SessionToken> validateToken(String value) {
    return delegate.validateToken(value);
  }

  /** An unknown or expired token logs nobody out, so there is nothing to record. */
  @Override
  public void logout(String value) {
    Optional<UserId> who = delegate.validateToken(value).map(SessionToken::userId);
    if (who.isEmpty()) {
      delegate.logout(value);
      return;
    }
    audited(() -> delegate.logout(value), () -> entry(who.get(), AuditAction.LOGGED_OUT));
  }

  @Override
  public SessionToken accessWithLink(String link) {
    return audited(
        () -> delegate.accessWithLink(link),
        token -> Optional.of(entry(token.userId(), AuditAction.LINK_USED)));
  }

  @Override
  public boolean authorizeResource(UserId u, ItemId r, AccessType acc) {
    return delegate.authorizeResource(u, r, acc);
  }

  @Override
  public SharedLinkSnapshot shareCollection(
      UserId u, ItemId col, PermissionLevel level, Duration lifetime) {
    return audited(
        () -> delegate.shareCollection(u, col, level, lifetime),
        link ->
            Optional.of(
                entry(u, AuditAction.LINK_CREATED)
                    .about(col)
                    .concerning(link.guestId())
                    .saying(level + " until " + link.expiresAt())));
  }

  /** Records the username and role, never the password. */
  @Override
  public UserId createUser(UserId admin, String username, String password, Role role) {
    return audited(
        () -> delegate.createUser(admin, username, password, role),
        created ->
            Optional.of(
                entry(admin, AuditAction.USER_CREATED)
                    .concerning(created)
                    .saying(username + " as " + role)));
  }

  @Override
  public void manageRoles(UserId admin, UserId target, Role role) {
    audited(
        () -> delegate.manageRoles(admin, target, role),
        () -> entry(admin, AuditAction.ROLE_CHANGED).concerning(target).saying(role.name()));
  }

  @Override
  public void grantPermission(UserId granter, UserId u, Permission p) {
    audited(
        () -> delegate.grantPermission(granter, u, p),
        () ->
            entry(granter, AuditAction.PERMISSION_GRANTED)
                .about(p.resource())
                .concerning(u)
                .saying(p.level().name()));
  }

  @Override
  public void revokePermission(UserId granter, UserId u, Permission p) {
    audited(
        () -> delegate.revokePermission(granter, u, p),
        () ->
            entry(granter, AuditAction.PERMISSION_REVOKED)
                .about(p.resource())
                .concerning(u)
                .saying(p.level().name()));
  }

  @Override
  public void deactivateUser(UserId admin, UserId u) {
    audited(
        () -> delegate.deactivateUser(admin, u),
        () -> entry(admin, AuditAction.USER_DEACTIVATED).concerning(u));
  }

  @Override
  public List<UserSnapshot> listUsers(UserId admin) {
    return delegate.listUsers(admin);
  }

  @Override
  public UserId findUser(UserId u, String username) {
    return delegate.findUser(u, username);
  }

  @Override
  public List<AccessGrant> accessTo(UserId u, ItemId resource) {
    return delegate.accessTo(u, resource);
  }
}
