package co.omnistream.domain.event;

import co.omnistream.domain.model.catalog.ItemId;
import java.time.Instant;

/** The metadata or the tags of a file changed. */
public final class FileModified extends CatalogEvent {

  public FileModified(ItemId itemId, Instant occurredAt) {
    super(itemId, occurredAt);
  }

  public FileModified(EventId id, Instant occurredAt, ItemId itemId) {
    super(id, occurredAt, itemId);
  }

  @Override
  public ChangeType changeType() {
    return ChangeType.MODIFIED;
  }
}
