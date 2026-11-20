package co.omnistream.domain.port.in;

import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.Credentials;
import co.omnistream.domain.port.dto.SessionToken;
import co.omnistream.domain.snapshot.SharedLinkSnapshot;
import java.time.Duration;
import java.util.Optional;

/** Driving port: implemented by a domain service, called by primary adapters. */
public interface IAccessControl {
  SessionToken authenticateUser(Credentials c);

  Optional<SessionToken> validateToken(String value);

  void logout(String value);

  SessionToken accessWithLink(String link);

  boolean authorizeResource(UserId u, ItemId r, AccessType acc);

  SharedLinkSnapshot shareCollection(
      UserId u, ItemId col, PermissionLevel level, Duration lifetime);

  UserId createUser(UserId admin, String username, String password, Role role);

  void manageRoles(UserId admin, UserId target, Role role);

  void grantPermission(UserId admin, UserId u, Permission p);

  void revokePermission(UserId admin, UserId u, Permission p);

  void deactivateUser(UserId admin, UserId u);
}
