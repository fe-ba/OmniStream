package co.omnistream.domain.model.access;

/** What a user is in the system, before looking at its permissions. */
public enum Role {
  /** Can do everything, on every resource, without needing permissions. */
  ADMIN,
  /** Can do what its permissions allow. */
  USER,
  /**
   * Created by sharing a collection ({@link User#createGuest}); has only the permission the shared
   * link gave it.
   */
  GUEST
}
