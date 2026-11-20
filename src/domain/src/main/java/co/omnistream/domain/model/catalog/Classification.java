package co.omnistream.domain.model.catalog;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Result of the classification rules for one file: the tags to set and the collections to add it to
 * (ADR 0016).
 *
 * <p>Rules only produce automatic tags. {@code CatalogIndex.applyClassification} replaces the
 * file's automatic tags with these and leaves the manual ones untouched.
 *
 * @param tags automatic tags for the file
 * @param collections collections the file is added to
 */
public record Classification(Set<Tag> tags, Set<ItemId> collections) {

  private static final Classification EMPTY = new Classification(Set.of(), Set.of());

  public Classification {
    tags = Set.copyOf(tags);
    collections = Set.copyOf(collections);
    for (Tag tag : tags) {
      if (!tag.isAutomatic()) {
        throw new IllegalArgumentException(
            "Classification rules only produce automatic tags: '" + tag.name() + "'");
      }
    }
  }

  /** A classification that sets no tags and adds to no collection. */
  public static Classification empty() {
    return EMPTY;
  }

  /** Joins the tags and collections of both classifications. */
  public Classification combine(Classification other) {
    Objects.requireNonNull(other, "other");
    Set<Tag> allTags = new HashSet<>(tags);
    allTags.addAll(other.tags);
    Set<ItemId> allCollections = new HashSet<>(collections);
    allCollections.addAll(other.collections);
    return new Classification(allTags, allCollections);
  }

  /** Whether no rule decided anything for the file. */
  public boolean isEmpty() {
    return tags.isEmpty() && collections.isEmpty();
  }
}
