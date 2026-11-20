package co.omnistream.domain.model.catalog;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of a catalog ({@link CatalogIndex}).
 *
 * @param value the UUID behind the identifier
 */
public record CatalogId(UUID value) {

  public CatalogId {
    Objects.requireNonNull(value, "value");
  }
}
