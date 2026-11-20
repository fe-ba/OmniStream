package co.omnistream.domain.snapshot;

import co.omnistream.domain.model.catalog.ItemId;
import java.util.List;

/** Immutable snapshot of an aggregate's state (Memento, ADR 0011). */
public record CollectionSnapshot(ItemId id, String name, String description, List<ItemId> items)
    implements CatalogItemSnapshot {}
