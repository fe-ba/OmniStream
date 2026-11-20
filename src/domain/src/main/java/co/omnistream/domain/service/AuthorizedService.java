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

  protected User loadUser(UserId u) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  protected void checkAccess(UserId u, ItemId r, AccessType acc) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  protected void requireAdmin(UserId admin) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
