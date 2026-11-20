package co.omnistream.domain.model.catalog;

/** Value object. */
public record IngestionResult(ItemId fileId, boolean duplicate, DuplicateAction action) {
  public boolean wasIndexed() {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
