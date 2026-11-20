package co.omnistream.domain.event;

import co.omnistream.domain.model.catalog.ItemId;
import java.time.Instant;

/** The items of a collection changed. */
public final class CollectionModified extends CatalogEvent {

  public CollectionModified(ItemId itemId, Instant occurredAt) {
    super(itemId, occurredAt);
  }

  public CollectionModified(EventId id, Instant occurredAt, ItemId itemId) {
    super(id, occurredAt, itemId);
  }

  @Override
  public ChangeType changeType() {
    return ChangeType.MODIFIED;
  }
}
