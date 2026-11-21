package co.omnistream.presentation.rest;

import co.omnistream.domain.exception.InvalidCredentials;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.sharing.SharedLinkId;
import co.omnistream.domain.port.dto.AccessGrant;
import co.omnistream.domain.port.dto.Credentials;
import co.omnistream.domain.port.dto.LocalCredentials;
import co.omnistream.domain.port.dto.SessionToken;
import co.omnistream.domain.port.in.IAccessControl;
import co.omnistream.domain.snapshot.SharedLinkSnapshot;
import co.omnistream.domain.snapshot.UserSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Access control with one known session ({@link #TOKEN}, for {@link #ADMIN}) that records every
 * call it receives in {@link #calls}.
 */
final class FakeAccessControl implements IAccessControl {

  static final String TOKEN = "good-token";
  static final UserId ADMIN = new UserId(UUID.fromString("00000000-0000-4000-8000-0000000000a1"));
  static final UserId CREATED = new UserId(UUID.fromString("00000000-0000-4000-8000-0000000000c1"));
  static final ItemId SHARED = new ItemId(UUID.fromString("00000000-0000-4000-8000-0000000000f1"));
  static final Instant EXPIRES = Instant.parse("2026-11-21T03:00:00Z");

  final List<String> calls = new ArrayList<>();

  private static SessionToken session(String value) {
    return new SessionToken(value, ADMIN, Role.ADMIN, EXPIRES);
  }

  @Override
  public SessionToken authenticateUser(Credentials c) {
    LocalCredentials local = (LocalCredentials) c;
    calls.add("authenticateUser " + c.provider() + " " + local.username());
    if (!local.password().equals("secret")) {
      throw new InvalidCredentials("Wrong username or password");
    }
    return session(TOKEN);
  }

  @Override
  public Optional<SessionToken> validateToken(String value) {
    return value.equals(TOKEN) ? Optional.of(session(TOKEN)) : Optional.empty();
  }

  @Override
  public void logout(String value) {
    calls.add("logout " + value);
  }

  @Override
  public SessionToken accessWithLink(String link) {
    calls.add("accessWithLink " + link);
    return session(TOKEN);
  }

  @Override
  public boolean authorizeResource(UserId u, ItemId r, AccessType acc) {
    throw new AssertionError("The API never calls authorizeResource");
  }

  @Override
  public SharedLinkSnapshot shareCollection(
      UserId u, ItemId col, PermissionLevel level, Duration lifetime) {
    calls.add("shareCollection " + u.value() + " " + col.value() + " " + level + " " + lifetime);
    return new SharedLinkSnapshot(
        new SharedLinkId(UUID.randomUUID()), "link-token", col, CREATED, level, u, EXPIRES, false);
  }

  @Override
  public UserId createUser(UserId admin, String username, String password, Role role) {
    calls.add("createUser " + admin.value() + " " + username + " " + role);
    return CREATED;
  }

  @Override
  public void manageRoles(UserId admin, UserId target, Role role) {
    calls.add("manageRoles " + admin.value() + " " + target.value() + " " + role);
  }

  @Override
  public void grantPermission(UserId admin, UserId u, Permission p) {
    calls.add("grantPermission " + u.value() + " " + p.resource().value() + " " + p.level());
  }

  @Override
  public void revokePermission(UserId admin, UserId u, Permission p) {
    calls.add("revokePermission " + u.value() + " " + p.resource().value() + " " + p.level());
  }

  @Override
  public void deactivateUser(UserId admin, UserId u) {
    calls.add("deactivateUser " + admin.value() + " " + u.value());
  }

  @Override
  public List<UserSnapshot> listUsers(UserId admin) {
    calls.add("listUsers " + admin.value());
    return List.of(
        new UserSnapshot(
            CREATED,
            "luis",
            Role.USER,
            Set.of(new Permission(SHARED, PermissionLevel.WRITE)),
            Set.of(new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, "luis")),
            true));
  }

  @Override
  public UserId findUser(UserId u, String username) {
    calls.add("findUser " + u.value() + " " + username);
    if (!username.equals("luis")) {
      throw new ResourceNotFound("No user called " + username);
    }
    return CREATED;
  }

  @Override
  public List<AccessGrant> accessTo(UserId u, ItemId resource) {
    calls.add("accessTo " + u.value() + " " + resource.value());
    return List.of(new AccessGrant(CREATED, "luis", PermissionLevel.WRITE));
  }
}
