package co.omnistream.domain.snapshot;

import co.omnistream.domain.model.catalog.ItemId;

/** State shared by the snapshots of files and collections (Memento, ADR 0011). */
public interface CatalogItemSnapshot {

  /** The item. */
  ItemId id();

  /** Name of the item. */
  String name();
}
