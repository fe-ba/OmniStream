package co.omnistream.domain.model.catalog;

import co.omnistream.domain.event.CatalogEvent;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Aggregate root of the catalog; originator of CatalogSnapshot (Memento). */
public final class CatalogIndex {
  private CatalogId id;
  private String name;
  private Map<Fingerprint, MediaFile> files;
  private List<Collection> collections;
  private List<CatalogEvent> events;
  private long version;

  private CatalogIndex() {}

  public IngestionResult ingest(MediaFile file, Instant now) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Optional<MediaFile> findByFingerprint(Fingerprint h) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Optional<CatalogItem> findItem(ItemId id) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public boolean hasDuplicate(Fingerprint h) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void editMetadata(ItemId id, MetadataEntry m, Instant now) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void tag(ItemId id, Tag e, Instant now) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void applyClassification(ItemId id, Classification c, Instant now) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void delete(ItemId id, Instant now) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Collection createCollection(String name, Instant now) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void addToCollection(ItemId collection, ItemId item, Instant now) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public int totalFiles() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public List<CatalogEvent> pullEvents() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public CatalogSnapshot toSnapshot() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public static CatalogIndex fromSnapshot(CatalogSnapshot i) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  private void register(CatalogEvent e) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
