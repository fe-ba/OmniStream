package co.omnistream.domain.event;

import co.omnistream.domain.model.catalog.ItemId;
import java.time.Instant;

/** A file was added to the catalog. */
public final class FileIngested extends CatalogEvent {

  public FileIngested(ItemId itemId, Instant occurredAt) {
    super(itemId, occurredAt);
  }

  public FileIngested(EventId id, Instant occurredAt, ItemId itemId) {
    super(id, occurredAt, itemId);
  }

  @Override
  public ChangeType changeType() {
    return ChangeType.ADDED;
  }
}
