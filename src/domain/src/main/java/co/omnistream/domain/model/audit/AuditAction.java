package co.omnistream.domain.model.audit;

/**
 * What a user did, as recorded in the audit trail.
 *
 * <p>Only actions that change something, or that matter for security, are recorded. Browsing,
 * searching and playing are not: they would bury the changes, and what a person watches is their
 * own business (ADR 0025).
 */
public enum AuditAction {
  // ---------------------------------------------------------------- access
  /** Logged in. */
  LOGGED_IN,
  /** Someone tried to log in to this user's account with the wrong password. */
  LOGIN_FAILED,
  /** Logged out. */
  LOGGED_OUT,
  /** Created a shared link to a collection. The subject is the link's guest. */
  LINK_CREATED,
  /** Opened a shared link. The actor is the link's guest. */
  LINK_USED,
  /** Created the subject's account. */
  USER_CREATED,
  /** Changed the subject's role. */
  ROLE_CHANGED,
  /** Gave the subject a permission on the resource. */
  PERMISSION_GRANTED,
  /** Took a permission on the resource away from the subject. */
  PERMISSION_REVOKED,
  /** Deactivated the subject's account. */
  USER_DEACTIVATED,

  // ---------------------------------------------------------------- catalog
  /** Added a file, by ingesting it from disk or by uploading it. */
  FILE_ADDED,
  /** Deleted a file. */
  FILE_DELETED,
  /** Changed a metadata entry of a file. */
  METADATA_EDITED,
  /** Saved a text file from the editor, with a different content (ADR 0027). */
  FILE_EDITED,
  /** Tagged a file. */
  FILE_TAGGED,
  /** Created a collection. */
  COLLECTION_CREATED,
  /** Renamed a collection or added items to it. */
  COLLECTION_MODIFIED,
  /** Downloaded a file to keep it offline. */
  FILE_DOWNLOADED,

  // ---------------------------------------------------------------- administration
  /** Indexed the library again. */
  LIBRARY_REINDEXED,
  /** Moved the library to another folder. */
  LIBRARY_PATH_CHANGED,
  /** Backed up the catalog. */
  BACKUP_CREATED
}
