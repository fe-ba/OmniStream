package co.omnistream.domain.model.access;

import co.omnistream.domain.model.catalog.ItemId;
import java.util.Objects;

/**
 * Lets a user work with one file or collection, up to a level.
 *
 * <p>A permission applies to exactly the item it names. A permission on a collection does not
 * reach, by itself, the files inside it: the catalog knows the tree, so the services resolve that
 * by also checking the collections that contain the item.
 *
 * @param resource the file or collection
 * @param level how much the user may do with it
 */
public record Permission(ItemId resource, PermissionLevel level) {

  public Permission {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(level, "level");
  }

  /** Whether this permission's level is enough for {@code access}. */
  public boolean allows(AccessType access) {
    return level.compareTo(requiredLevel(Objects.requireNonNull(access, "access"))) >= 0;
  }

  /** Whether this permission is about {@code resource}. */
  public boolean appliesTo(ItemId resource) {
    return this.resource.equals(Objects.requireNonNull(resource, "resource"));
  }

  private static PermissionLevel requiredLevel(AccessType access) {
    return switch (access) {
      case VIEW, DOWNLOAD -> PermissionLevel.READ;
      case MODIFY -> PermissionLevel.WRITE;
      case DELETE -> PermissionLevel.MANAGE;
    };
  }
}
