package co.omnistream.domain.event;

import co.omnistream.domain.model.catalog.ItemId;
import java.time.Instant;

/** A file was removed from the catalog. */
public final class FileDeleted extends CatalogEvent {

  public FileDeleted(ItemId itemId, Instant occurredAt) {
    super(itemId, occurredAt);
  }

  public FileDeleted(EventId id, Instant occurredAt, ItemId itemId) {
    super(id, occurredAt, itemId);
  }

  @Override
  public ChangeType changeType() {
    return ChangeType.REMOVED;
  }
}
