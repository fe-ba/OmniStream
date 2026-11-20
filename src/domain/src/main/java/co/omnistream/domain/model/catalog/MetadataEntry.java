package co.omnistream.domain.model.catalog;

import java.util.Objects;

/**
 * One metadata value of a file, such as the camera, the codec or the capture date.
 *
 * <p>Extracted entries come from the metadata extractor; manual entries are set or edited by a
 * user. Editing a value always makes it manual, so the catalog can tell what a user wrote from what
 * was extracted.
 *
 * @param key name of the metadata, without surrounding spaces
 * @param value the value, as text
 * @param source whether the value was extracted or set by a user
 */
public record MetadataEntry(String key, String value, MetadataSource source) {

  public MetadataEntry {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(source, "source");
    key = key.strip();
    if (key.isEmpty()) {
      throw new IllegalArgumentException("A metadata entry needs a key");
    }
  }

  /** Whether a user set or edited this value. */
  public boolean isManual() {
    return source == MetadataSource.MANUAL;
  }

  /**
   * Returns this entry with another value. The result is always {@link MetadataSource#MANUAL},
   * because only a user edits a value.
   */
  public MetadataEntry withValue(String v) {
    return new MetadataEntry(key, v, MetadataSource.MANUAL);
  }
}
