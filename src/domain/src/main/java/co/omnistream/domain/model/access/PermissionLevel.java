package co.omnistream.domain.model.access;

/**
 * How much a permission lets a user do with a resource.
 *
 * <p>The levels are declared from lowest to highest and each one includes the ones before it, so
 * comparing two levels ({@link #compareTo}) tells which one allows more.
 */
public enum PermissionLevel {
  /** View and download. */
  READ,
  /** Also modify: edit metadata, tags and the contents of a collection. */
  WRITE,
  /** Also delete. */
  MANAGE
}
