package co.omnistream.domain.snapshot;

import co.omnistream.domain.model.catalog.ItemId;

/** Immutable snapshot of an aggregate's state (Memento, ADR 0011). */
public interface CatalogItemSnapshot {
  ItemId id();

  String name();
}
