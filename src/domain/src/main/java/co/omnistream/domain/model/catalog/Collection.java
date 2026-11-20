package co.omnistream.domain.model.catalog;

import co.omnistream.domain.exception.CyclicCollection;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Composite of the catalog tree (Composite): an ordered group of files and other collections.
 *
 * <p>A file can belong to several collections. A collection can never contain itself, directly or
 * through its sub-collections: {@link #add(CatalogItem)} checks it before adding.
 */
public final class Collection extends CatalogItem {

  private final String description;
  private final List<CatalogItem> items;

  private Collection(ItemId id, String name, String description) {
    super(id, name);
    this.description = Objects.requireNonNull(description, "description");
    this.items = new ArrayList<>();
  }

  /** A new, empty collection without description. Created through {@code CatalogIndex}. */
  static Collection create(String name) {
    return new Collection(new ItemId(UUID.randomUUID()), name, "");
  }

  /** Rebuilds a collection without its items; {@code CatalogIndex} links them afterwards. */
  static Collection restore(CollectionSnapshot s) {
    return new Collection(s.id(), s.name(), s.description());
  }

  /**
   * Adds a file or a collection at the end. Adding an item that is already a direct member does
   * nothing.
   *
   * @throws CyclicCollection if {@code e} is this collection or contains it
   */
  public void add(CatalogItem e) {
    Objects.requireNonNull(e, "e");
    if (wouldCreateCycle(e)) {
      throw new CyclicCollection(
          "Collection '" + name + "' cannot contain '" + e.name + "': it would contain itself");
    }
    if (!items.contains(e)) {
      items.add(e);
    }
  }

  /** Removes a direct member; removing an item that is not one does nothing. */
  public void remove(CatalogItem e) {
    Objects.requireNonNull(e, "e");
    items.remove(e);
  }

  @Override
  public long totalSize() {
    return files().stream().mapToLong(MediaFile::totalSize).sum();
  }

  @Override
  public int countFiles() {
    return files().size();
  }

  @Override
  public boolean contains(CatalogItem e) {
    Objects.requireNonNull(e, "e");
    for (CatalogItem item : items) {
      if (item.equals(e) || item.contains(e)) {
        return true;
      }
    }
    return false;
  }

  /** Levels of collections from this one down: 1 when it holds no sub-collection. */
  public int depth() {
    int deepest = 0;
    for (CatalogItem item : items) {
      if (item instanceof Collection sub) {
        deepest = Math.max(deepest, sub.depth());
      }
    }
    return 1 + deepest;
  }

  @Override
  public CollectionSnapshot toSnapshot() {
    return new CollectionSnapshot(
        id, name, description, items.stream().map(item -> item.id).toList());
  }

  @Override
  void collectFiles(Set<MediaFile> into) {
    items.forEach(item -> item.collectFiles(into));
  }

  /** Whether {@code e} is a direct member. */
  boolean hasItem(CatalogItem e) {
    return items.contains(e);
  }

  /** Puts {@code replacement} where {@code old} was, keeping the order. */
  void replaceItem(CatalogItem old, CatalogItem replacement) {
    items.set(items.indexOf(old), replacement);
  }

  /** Links an item while rebuilding from a snapshot, without checks (ADR 0011). */
  void restoreItem(CatalogItem e) {
    items.add(e);
  }

  private Set<MediaFile> files() {
    Set<MediaFile> files = new HashSet<>();
    collectFiles(files);
    return files;
  }

  private boolean wouldCreateCycle(CatalogItem e) {
    return e.equals(this) || e.contains(this);
  }
}
