package co.omnistream.domain.model.catalog;

/** What the deduplication policy decides when an incoming file is already in the catalog. */
public enum DuplicateAction {
  /** Keep the existing file and discard the incoming one. */
  REJECT,
  /** The incoming file takes the place of the existing one. */
  REPLACE,
  /** Index the incoming file too; both stay in the catalog. */
  KEEP_BOTH
}
