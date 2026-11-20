package co.omnistream.domain.event;

/** Kind of change written to the change log, so clients can synchronize. */
public enum ChangeType {
  /** The item is new. */
  ADDED,
  /** The item still exists but changed. */
  MODIFIED,
  /** The item no longer exists. */
  REMOVED
}
