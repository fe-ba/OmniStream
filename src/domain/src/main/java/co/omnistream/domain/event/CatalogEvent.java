package co.omnistream.domain.event;

import co.omnistream.domain.model.catalog.ItemId;

/** Domain event. */
public abstract class CatalogEvent extends DomainEvent {
  private ItemId itemId;

  public ItemId item() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public abstract ChangeType changeType();
}
