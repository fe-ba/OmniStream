package co.omnistream.domain.model.catalog;

import co.omnistream.domain.snapshot.CollectionSnapshot;
import java.util.List;

/** Composite of the catalog tree (Composite). */
public final class Collection extends CatalogItem {
  private String description;
  private List<CatalogItem> items;

  private Collection() {}

  public void add(CatalogItem e) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void remove(CatalogItem e) {
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

  public int depth() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public CollectionSnapshot toSnapshot() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  private boolean wouldCreateCycle(CatalogItem e) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
