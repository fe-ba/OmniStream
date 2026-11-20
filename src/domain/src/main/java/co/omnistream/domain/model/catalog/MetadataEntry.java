package co.omnistream.domain.model.catalog;

/** Value object. */
public record MetadataEntry(String key, String value, MetadataSource source) {
  public boolean isManual() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public MetadataEntry withValue(String v) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
