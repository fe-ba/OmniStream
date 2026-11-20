package co.omnistream.domain.snapshot;

import co.omnistream.domain.model.catalog.ItemId;
import java.util.List;

/**
 * State of a {@code Collection} (Memento, ADR 0011).
 *
 * @param id the collection
 * @param name name shown to users
 * @param description free text; empty when there is none
 * @param items files and collections it contains, in order
 */
public record CollectionSnapshot(ItemId id, String name, String description, List<ItemId> items)
    implements CatalogItemSnapshot {

  public CollectionSnapshot {
    items = List.copyOf(items);
  }
}
