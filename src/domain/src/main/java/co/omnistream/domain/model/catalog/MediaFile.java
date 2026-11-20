package co.omnistream.domain.model.catalog;

import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Leaf of the catalog tree (Composite). */
public final class MediaFile extends CatalogItem {
  private Path path;
  private Fingerprint fingerprint;
  private MediaType type;
  private long sizeBytes;
  private Duration duration;
  private IndexingStatus status;
  private Instant ingestedAt;
  private List<MetadataEntry> metadata;
  private Set<Tag> tags;

  private MediaFile() {}

  public static MediaFile create(
      String name,
      Path path,
      Fingerprint fingerprint,
      MediaType type,
      long sizeBytes,
      Duration duration,
      Instant now) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public boolean isDuplicateOf(MediaFile other) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void addMetadata(MetadataEntry m) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Optional<MetadataEntry> metadataEntry(String key) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void tag(Tag e) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void replaceAutomaticTags(Set<Tag> newTags) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void markIndexed() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void markFailed(String cause) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public long totalSize() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public int countFiles() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public boolean contains(CatalogItem e) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public MediaFileSnapshot toSnapshot() {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
