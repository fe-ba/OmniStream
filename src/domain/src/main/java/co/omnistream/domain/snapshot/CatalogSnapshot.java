package co.omnistream.domain.snapshot;

import co.omnistream.domain.model.catalog.CatalogId;
import java.util.List;

/** Immutable snapshot of an aggregate's state (Memento, ADR 0011). */
public record CatalogSnapshot(
    CatalogId id,
    String name,
    long version,
    List<MediaFileSnapshot> files,
    List<CollectionSnapshot> collections) {}
