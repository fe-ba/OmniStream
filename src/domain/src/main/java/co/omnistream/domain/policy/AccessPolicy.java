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
}
