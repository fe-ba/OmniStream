package co.omnistream.domain.event;

import co.omnistream.domain.model.catalog.ItemId;
import java.time.Instant;

/** A collection was created. */
public final class CollectionCreated extends CatalogEvent {

  public CollectionCreated(ItemId itemId, Instant occurredAt) {
    super(itemId, occurredAt);
  }

  public CollectionCreated(EventId id, Instant occurredAt, ItemId itemId) {
    super(id, occurredAt, itemId);
  }

  @Override
  public ChangeType changeType() {
    return ChangeType.ADDED;
  }
}
