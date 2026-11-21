package co.omnistream.domain.policy;

import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.snapshot.UserSnapshot;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Decides what a user may do with a file or collection. Stateless.
 *
 * <p>The rules, in order:
 *
 * <ol>
 *   <li>A deactivated user may do nothing.
 *   <li>An administrator may do everything, on every resource, without permissions.
 *   <li>Anyone else, guests included, may do what its permission on that resource allows ({@link
 *       Permission#allows}). Without a permission on it, nothing.
 * </ol>
 *
 * <p>It looks only at the resource it is given. Whether a permission on a collection covers the
 * files inside it is resolved by the services, which can see the catalog.
 *
 * <p>It also decides who may hand permissions to others ({@link #mayDelegate}, ADR 0024): an
 * administrator on everything, and whoever manages a resource on that resource, up to {@code
 * WRITE}.
 */
public final class AccessPolicy {

  public AccessPolicy() {}

  /** Whether {@code u} may do {@code access} on {@code resource}. */
  public boolean authorizes(User u, ItemId resource, AccessType access) {
    Objects.requireNonNull(access, "access");
    UserSnapshot user = Objects.requireNonNull(u, "u").toSnapshot();
    Objects.requireNonNull(resource, "resource");
    if (!user.active()) {
      return false;
    }
    if (user.role() == Role.ADMIN) {
      return true;
    }
    return user.permissions().stream()
        .anyMatch(permission -> permission.appliesTo(resource) && permission.allows(access));
  }

  /**
   * The highest level {@code u} has on {@code resource}: {@link PermissionLevel#MANAGE} for an
   * active administrator, empty if the user may do nothing with it. A service uses it, for example,
   * so that nobody shares a collection with more rights than they have.
   */
  public Optional<PermissionLevel> effectiveLevel(User u, ItemId resource) {
    UserSnapshot user = Objects.requireNonNull(u, "u").toSnapshot();
    Objects.requireNonNull(resource, "resource");
    if (!user.active()) {
      return Optional.empty();
    }
    if (user.role() == Role.ADMIN) {
      return Optional.of(PermissionLevel.MANAGE);
    }
    return user.permissions().stream()
        .filter(permission -> permission.appliesTo(resource))
        .map(Permission::level)
        .max(Comparator.naturalOrder());
  }

  /**
   * Whether {@code granter} may give {@code p} to {@code target}, or take it away. Both are the
   * same decision: whoever can do one can undo it (ADR 0024).
   *
   * <ol>
   *   <li>A deactivated granter may do neither, and neither may a guest: whoever holds a link
   *       cannot hand rights to the people of the server.
   *   <li>An administrator may, with any level.
   *   <li>Anyone else needs {@code MANAGE} on the resource, and then:
   *       <ul>
   *         <li>may only hand out {@code READ} or {@code WRITE}: who manages a resource is decided
   *             by an administrator;
   *         <li>may not change its own permissions;
   *         <li>may not touch someone who also manages the resource, administrators included, so
   *             that two managers cannot take the resource from each other.
   *       </ul>
   * </ol>
   *
   * <p>The granter may manage the resource through a collection that contains it, which only the
   * services can see in the catalog. They pass that level as {@code inheritedLevel}; the policy
   * adds the permissions the granter has on the resource itself.
   *
   * <p>Whether the target can hold permissions at all (guests cannot) is not a matter of rights:
   * {@code User.grant} and {@code User.revoke} check it.
   */
  public boolean mayDelegate(
      User granter, Optional<PermissionLevel> inheritedLevel, User target, Permission p) {
    UserSnapshot from = Objects.requireNonNull(granter, "granter").toSnapshot();
    Objects.requireNonNull(inheritedLevel, "inheritedLevel");
    UserSnapshot to = Objects.requireNonNull(target, "target").toSnapshot();
    Objects.requireNonNull(p, "p");
    if (!from.active() || from.role() == Role.GUEST) {
      return false;
    }
    if (from.role() == Role.ADMIN) {
      return true;
    }
    if (p.level() == PermissionLevel.MANAGE || from.id().equals(to.id())) {
      return false;
    }
    Optional<PermissionLevel> granterLevel =
        Stream.of(inheritedLevel, effectiveLevel(granter, p.resource()))
            .flatMap(Optional::stream)
            .max(Comparator.naturalOrder());
    if (granterLevel.filter(level -> level == PermissionLevel.MANAGE).isEmpty()) {
      return false;
    }
    return effectiveLevel(target, p.resource())
        .filter(level -> level == PermissionLevel.MANAGE)
        .isEmpty();
  }
}
