package co.omnistream.domain.model.catalog;

/** Who set a tag (ADR 0016). */
public enum TagSource {
  /** The classification rules; replaced every time the file is classified again. */
  AUTOMATIC,
  /** A user; classification never removes it. */
  MANUAL
}
