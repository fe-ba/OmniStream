package co.omnistream.domain.port.dto;

import co.omnistream.domain.model.catalog.ItemId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What {@code manageCollections} does: create a collection, or change an existing one.
 *
 * <ul>
 *   <li>Without {@code collectionId}: creates a collection called {@code name} with {@code
 *       itemsToAdd} in it. The name is required.
 *   <li>With {@code collectionId}: renames that collection if {@code name} is present, and adds
 *       {@code itemsToAdd} to it.
 * </ul>
 *
 * @param collectionId the collection to change; empty to create one
 * @param name the new name
 * @param itemsToAdd files and collections to put in it; may be empty
 */
public record CollectionCommand(
    Optional<ItemId> collectionId, Optional<String> name, List<ItemId> itemsToAdd) {

  public CollectionCommand {
    Objects.requireNonNull(collectionId, "collectionId");
    Objects.requireNonNull(name, "name");
    itemsToAdd = List.copyOf(itemsToAdd);
    if (collectionId.isEmpty() && name.isEmpty()) {
      throw new IllegalArgumentException("A new collection needs a name");
    }
  }
}
