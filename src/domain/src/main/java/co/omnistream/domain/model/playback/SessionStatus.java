package co.omnistream.domain.model.playback;

/** Where a playback session is. */
public enum SessionStatus {
  /** Playing. */
  ACTIVE,
  /** Stopped for a while; it can go on. */
  PAUSED,
  /** Closed; it no longer changes, but another session can resume from where it stopped. */
  FINISHED
}
