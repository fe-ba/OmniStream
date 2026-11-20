package co.omnistream.domain.service;

import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.dto.Credentials;
import co.omnistream.domain.port.dto.SessionToken;
import co.omnistream.domain.port.in.IAccessControl;
import co.omnistream.domain.port.out.ICredentialRegistry;
import co.omnistream.domain.port.out.IIdentityProvider;
import co.omnistream.domain.port.out.ISharedLinkRepository;
import co.omnistream.domain.port.out.ITokenRepository;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.snapshot.SharedLinkSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Domain service that implements the IAccessControl driving port. */
public final class AccessService extends AuthorizedService implements IAccessControl {
  private final List<IIdentityProvider> providers;
  private final ICredentialRegistry credentialRegistry;
  private final ITokenRepository tokenRepository;
  private final ISharedLinkRepository sharedLinkRepository;
  private final IUnitOfWork unitOfWork;

  public AccessService(
      IUserRepository userRepository,
      AccessPolicy accessPolicy,
      Clock clock,
      List<IIdentityProvider> providers,
      ICredentialRegistry credentialRegistry,
      ITokenRepository tokenRepository,
      ISharedLinkRepository sharedLinkRepository,
      IUnitOfWork unitOfWork) {
    super(userRepository, accessPolicy, clock);
    this.providers = providers;
    this.credentialRegistry = credentialRegistry;
    this.tokenRepository = tokenRepository;
    this.sharedLinkRepository = sharedLinkRepository;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public SessionToken authenticateUser(Credentials c) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Optional<SessionToken> validateToken(String value) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void logout(String value) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public SessionToken accessWithLink(String link) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public boolean authorizeResource(UserId u, ItemId r, AccessType acc) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public SharedLinkSnapshot shareCollection(
      UserId u, ItemId col, PermissionLevel level, Duration lifetime) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public UserId createUser(UserId admin, String username, String password, Role role) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void manageRoles(UserId admin, UserId target, Role role) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void grantPermission(UserId admin, UserId u, Permission p) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void revokePermission(UserId admin, UserId u, Permission p) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void deactivateUser(UserId admin, UserId u) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  private IIdentityProvider selectProvider(Credentials c) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  private SessionToken issueToken(User u) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
