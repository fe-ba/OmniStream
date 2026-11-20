package co.omnistream.domain.model.catalog;

import co.omnistream.domain.snapshot.CatalogItemSnapshot;
import java.util.Objects;
import java.util.Set;

/**
 * Component of the catalog tree (Composite): a {@link MediaFile} is a leaf and a {@link Collection}
 * a composite that holds files and other collections.
 *
 * <p>{@link #totalSize()}, {@link #countFiles()} and {@link #contains(CatalogItem)} are resolved
 * recursively over the tree; the leaf ends the recursion. Items are equal when their identifiers
 * are equal.
 */
public abstract class CatalogItem {

  protected final ItemId id;
  protected String name;

  protected CatalogItem(ItemId id, String name) {
    this.id = Objects.requireNonNull(id, "id");
    this.name = validName(name);
  }

  /** Size in bytes of every file under this item; a file shared by two branches counts once. */
  public abstract long totalSize();

  /** Number of distinct files under this item. */
  public abstract int countFiles();

  /** Whether {@code e} is somewhere under this item. An item does not contain itself. */
  public abstract boolean contains(CatalogItem e);

  /** State of this item (Memento, ADR 0011). */
  public abstract CatalogItemSnapshot toSnapshot();

  /** Changes the name shown to users. */
  public void rename(String name) {
    this.name = validName(name);
  }

  /** Adds every file under this item to {@code into} (recursive step of the Composite). */
  abstract void collectFiles(Set<MediaFile> into);

  private static String validName(String name) {
    Objects.requireNonNull(name, "name");
    String stripped = name.strip();
    if (stripped.isEmpty()) {
      throw new IllegalArgumentException("A catalog item needs a name");
    }
    return stripped;
  }

  @Override
  public final boolean equals(Object other) {
    return other instanceof CatalogItem item && id.equals(item.id);
  }

  @Override
  public final int hashCode() {
    return id.hashCode();
  }
}
