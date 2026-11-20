package co.omnistream.domain.model.access;

/** What a user wants to do with a resource; {@link Permission#allows} decides if it may. */
public enum AccessType {
  /** Browse it or play it. Needs {@link PermissionLevel#READ}. */
  VIEW,
  /** Download it for offline use. Needs {@link PermissionLevel#READ}. */
  DOWNLOAD,
  /** Change it. Needs {@link PermissionLevel#WRITE}. */
  MODIFY,
  /** Delete it. Needs {@link PermissionLevel#MANAGE}. */
  DELETE
}
