package co.omnistream.domain.service;

import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.out.IUserRepository;
import java.time.Clock;

/** Base class of the services that authorize a user. */
public abstract class AuthorizedService {
  protected final IUserRepository userRepository;
  protected final AccessPolicy accessPolicy;
  protected final Clock clock;

  protected AuthorizedService(
      IUserRepository userRepository, AccessPolicy accessPolicy, Clock clock) {
    this.userRepository = userRepository;
    this.accessPolicy = accessPolicy;
    this.clock = clock;
  }

  /**
   * The user, or {@code ResourceNotFound} if it does not exist. Fresh from the repository on every
   * call: roles and permissions may have changed since the last request.
   */
  protected User loadUser(UserId u) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  /**
   * Throws {@code AccessDenied} unless {@code u} may do {@code acc} on {@code r}.
   *
   * <p>A user may also reach {@code r} through any collection that contains it, directly or through
   * other collections. {@code accessPolicy.authorizes(user, r, acc, catalog)} resolves that with a
   * {@code CatalogSnapshot} of the catalog.
   */
  protected void checkAccess(UserId u, ItemId r, AccessType acc) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  /**
   * Throws {@code AccessDenied} unless {@code admin} is an administrator. {@code User.isAdmin()} is
   * already false for a deactivated one.
   */
  protected void requireAdmin(UserId admin) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
