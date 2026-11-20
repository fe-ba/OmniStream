package co.omnistream.domain.snapshot;

import co.omnistream.domain.model.catalog.CatalogId;
import java.util.List;

/**
 * State of a {@code CatalogIndex} (Memento, ADR 0011). {@code collections} lists every collection,
 * nested ones included; nesting is in {@link CollectionSnapshot#items()}.
 *
 * <p>{@code version} is the one the catalog was loaded with; the repository compares it on save
 * (optimistic locking).
 *
 * @param id the catalog
 * @param name name of the catalog
 * @param version version for optimistic locking
 * @param files every file of the catalog
 * @param collections every collection of the catalog
 */
public record CatalogSnapshot(
    CatalogId id,
    String name,
    long version,
    List<MediaFileSnapshot> files,
    List<CollectionSnapshot> collections) {

  public CatalogSnapshot {
    files = List.copyOf(files);
    collections = List.copyOf(collections);
  }
}
