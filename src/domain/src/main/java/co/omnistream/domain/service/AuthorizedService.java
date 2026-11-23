package co.omnistream.domain.service;

import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import java.time.Clock;
import java.util.Objects;

/** Base class of the services that authorize a user. */
public abstract class AuthorizedService {
  protected final IUserRepository userRepository;
  protected final AccessPolicy accessPolicy;
  protected final Clock clock;

  protected AuthorizedService(
      IUserRepository userRepository, AccessPolicy accessPolicy, Clock clock) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * The user, or {@code ResourceNotFound} if it does not exist. Fresh from the repository on every
   * call: roles and permissions may have changed since the last request.
   */
  protected User loadUser(UserId u) {
    Objects.requireNonNull(u, "u");
    return userRepository
        .findById(u)
        .orElseThrow(() -> new ResourceNotFound("User not found: " + u.value()));
  }

  /**
   * Throws {@code AccessDenied} unless {@code u} may do {@code acc} on {@code r}.
   *
   * <p>Checks only the permission on {@code r} itself. Services that work with catalog items should
   * use {@link #checkAccess(UserId, ItemId, AccessType, CatalogSnapshot)} so permissions granted on
   * enclosing collections also apply.
   */
  protected void checkAccess(UserId u, ItemId r, AccessType acc) {
    Objects.requireNonNull(r, "r");
    Objects.requireNonNull(acc, "acc");
    User user = loadUser(u);
    if (!accessPolicy.authorizes(user, r, acc)) {
      throw new AccessDenied(user.toSnapshot().username() + " may not " + acc + " " + r.value());
    }
  }

  /**
   * Throws {@code AccessDenied} unless {@code u} may do {@code acc} on {@code r}, counting any
   * collection of {@code catalog} that contains it directly or transitively.
   */
  protected void checkAccess(UserId u, ItemId r, AccessType acc, CatalogSnapshot catalog) {
    Objects.requireNonNull(r, "r");
    Objects.requireNonNull(acc, "acc");
    Objects.requireNonNull(catalog, "catalog");
    User user = loadUser(u);
    if (!accessPolicy.authorizes(user, r, acc, catalog)) {
      throw new AccessDenied(user.toSnapshot().username() + " may not " + acc + " " + r.value());
    }
  }

  /**
   * Throws {@code AccessDenied} unless {@code admin} is an administrator. {@code User.isAdmin()} is
   * already false for a deactivated one.
   */
  protected void requireAdmin(UserId admin) {
    User user = loadUser(admin);
    if (!user.isAdmin()) {
      throw new AccessDenied("Administrator privileges required");
    }
  }
}
