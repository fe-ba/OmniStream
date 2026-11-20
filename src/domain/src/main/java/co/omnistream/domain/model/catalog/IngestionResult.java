package co.omnistream.domain.model.catalog;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of ingesting one file into the catalog ({@code CatalogIndex.ingest}).
 *
 * <p>A new file has no action. A duplicate always has one: what the deduplication policy decided.
 * {@code fileId} is the file that was indexed or, when the incoming file is rejected, the file
 * already in the catalog that it duplicates.
 *
 * @param fileId the file the result refers to
 * @param duplicate whether the file's fingerprint was already in the catalog
 * @param action what was done with the duplicate; empty for a new file
 */
public record IngestionResult(ItemId fileId, boolean duplicate, Optional<DuplicateAction> action) {

  public IngestionResult {
    Objects.requireNonNull(fileId, "fileId");
    Objects.requireNonNull(action, "action");
    if (duplicate != action.isPresent()) {
      throw new IllegalArgumentException(
          "A duplicate needs an action, and only a duplicate has one");
    }
  }

  /** Whether the incoming file ended up in the catalog: always, unless it was rejected. */
  public boolean wasIndexed() {
    return action.map(a -> a != DuplicateAction.REJECT).orElse(true);
  }
}
