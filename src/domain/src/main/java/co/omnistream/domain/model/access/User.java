package co.omnistream.domain.model.access;

import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.snapshot.UserSnapshot;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Root of the access aggregate: someone who can log in, with a role and permissions on files and
 * collections.
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>A user has at most one permission per resource; granting another one replaces it.
 *   <li>A user always has at least one linked identity.
 *   <li>Guests are created only with {@link #createGuest}. Their permission, role and identity come
 *       from the shared link and do not change. Nobody else can become a guest.
 *   <li>A deactivated user can do nothing, not even as an administrator.
 * </ul>
 *
 * <p>The rule that decides access is the {@link AccessPolicy}; {@link #canAccess} asks it.
 */
public final class User {

  private static final AccessPolicy ACCESS = new AccessPolicy();

  private final UserId id;
  private final String username;
  private Role role;
  private final Set<Permission> permissions;
  private final Set<LinkedIdentity> identities;
  private boolean active;

  private User(
      UserId id,
      String username,
      Role role,
      Set<Permission> permissions,
      Set<LinkedIdentity> identities,
      boolean active) {
    this.id = Objects.requireNonNull(id, "id");
    this.username = Objects.requireNonNull(username, "username");
    this.role = Objects.requireNonNull(role, "role");
    this.permissions = new LinkedHashSet<>(permissions);
    this.identities = new LinkedHashSet<>(identities);
    this.active = active;
  }

  /**
   * A new active user without permissions.
   *
   * @param username name shown to others; surrounding spaces are removed
   * @param role {@link Role#ADMIN} or {@link Role#USER}
   * @param i the identity it logs in with, usually {@code ("local", username)}
   * @throws IllegalArgumentException if the username is blank or the role is {@link Role#GUEST}
   */
  public static User create(String username, Role role, LinkedIdentity i) {
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(i, "i");
    if (role == Role.GUEST) {
      throw new IllegalArgumentException("Guests are created with createGuest");
    }
    return new User(newId(), validUsername(username), role, Set.of(), Set.of(i), true);
  }

  /**
   * The guest behind a shared link: it can do only what {@code level} allows on {@code resource}.
   * Its username is {@code guest-<id>} and its identity is {@code ("shared-link", <id>)}.
   */
  public static User createGuest(ItemId resource, PermissionLevel level) {
    Permission permission = new Permission(resource, level);
    UserId id = newId();
    String subject = id.value().toString();
    return new User(
        id,
        "guest-" + subject,
        Role.GUEST,
        Set.of(permission),
        Set.of(new LinkedIdentity(LinkedIdentity.SHARED_LINK_ISSUER, subject)),
        true);
  }

  /**
   * Whether this user may do {@code access} on {@code resource}, as the {@link AccessPolicy} says.
   */
  public boolean canAccess(ItemId resource, AccessType access) {
    return ACCESS.authorizes(this, resource, access);
  }

  /**
   * Gives the user {@code p}. If it already had a permission on the same resource, {@code p}
   * replaces it, so the level can go up or down.
   *
   * @throws IllegalStateException if the user is a guest
   */
  public void grant(Permission p) {
    Objects.requireNonNull(p, "p");
    requireNotGuest("change a guest's permission");
    permissions.removeIf(current -> current.appliesTo(p.resource()));
    permissions.add(p);
  }

  /**
   * Takes {@code p} away. Nothing happens if the user does not have exactly that permission.
   *
   * @throws IllegalStateException if the user is a guest
   */
  public void revoke(Permission p) {
    Objects.requireNonNull(p, "p");
    requireNotGuest("change a guest's permission");
    permissions.remove(p);
  }

  /**
   * Makes the user an administrator or a regular user.
   *
   * @throws IllegalArgumentException if {@code r} is {@link Role#GUEST}
   * @throws IllegalStateException if the user is a guest
   */
  public void changeRole(Role r) {
    Objects.requireNonNull(r, "r");
    requireNotGuest("change a guest's role");
    if (r == Role.GUEST) {
      throw new IllegalArgumentException(
          "Nobody can become a guest; guests come from shared links");
    }
    role = r;
  }

  /**
   * Adds another identity the user can log in with. Linking one it already has does nothing.
   *
   * @throws IllegalStateException if the user is a guest
   */
  public void link(LinkedIdentity i) {
    Objects.requireNonNull(i, "i");
    requireNotGuest("link an identity to a guest");
    identities.add(i);
  }

  /** Whether the user is an active administrator. A deactivated administrator is not. */
  public boolean isAdmin() {
    return active && role == Role.ADMIN;
  }

  /** From now on the user can do nothing. Deactivating it again does nothing. */
  public void deactivate() {
    active = false;
  }

  /** The user's current state (Memento, ADR 0011). */
  public UserSnapshot toSnapshot() {
    return new UserSnapshot(id, username, role, permissions, identities, active);
  }

  /** Rebuilds a stored user exactly as it was, without checking the creation rules. */
  public static User fromSnapshot(UserSnapshot i) {
    Objects.requireNonNull(i, "i");
    return new User(i.id(), i.username(), i.role(), i.permissions(), i.identities(), i.active());
  }

  /** Two users are the same if they have the same identifier. */
  @Override
  public boolean equals(Object other) {
    return other instanceof User user && id.equals(user.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  private void requireNotGuest(String action) {
    if (role == Role.GUEST) {
      throw new IllegalStateException("Cannot " + action + ": it comes from its shared link");
    }
  }

  private static UserId newId() {
    return new UserId(UUID.randomUUID());
  }

  private static String validUsername(String username) {
    String stripped = Objects.requireNonNull(username, "username").strip();
    if (stripped.isEmpty()) {
      throw new IllegalArgumentException("A user needs a username");
    }
    return stripped;
  }
}
