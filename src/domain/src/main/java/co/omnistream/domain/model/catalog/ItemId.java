package co.omnistream.domain.model.catalog;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of an item of the catalog: a file or a collection ({@link CatalogItem}).
 *
 * <p>Other aggregates refer to files and collections only through this identifier.
 *
 * @param value the UUID behind the identifier
 */
public record ItemId(UUID value) {

  public ItemId {
    Objects.requireNonNull(value, "value");
  }
}
