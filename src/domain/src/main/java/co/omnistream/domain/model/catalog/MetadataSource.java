package co.omnistream.domain.model.catalog;

/** Where a metadata value comes from. */
public enum MetadataSource {
  /** Read from the file by the metadata extractor. */
  EXTRACTED,
  /** Set or edited by a user. */
  MANUAL
}
