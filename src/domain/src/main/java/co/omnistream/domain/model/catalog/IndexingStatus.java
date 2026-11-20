package co.omnistream.domain.model.catalog;

/** Where a media file is in the ingestion process. */
public enum IndexingStatus {
  /** Registered, not processed yet. */
  PENDING,
  /** Being processed: metadata extraction and classification. */
  INDEXING,
  /** Processed and searchable. */
  INDEXED,
  /** Processing failed; the file stays in the catalog so it can be retried. */
  ERROR
}
