package co.omnistream.domain.model.catalog;

import co.omnistream.domain.snapshot.CatalogItemSnapshot;

/** Component of the catalog tree (Composite). */
public abstract class CatalogItem {
  protected ItemId id;
  protected String name;

  protected CatalogItem() {}

  public abstract long totalSize();

  public abstract int countFiles();

  public abstract boolean contains(CatalogItem e);

  public abstract CatalogItemSnapshot toSnapshot();

  public void rename(String name) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
