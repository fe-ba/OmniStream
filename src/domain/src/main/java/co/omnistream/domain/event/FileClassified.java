package co.omnistream.domain.event;

import co.omnistream.domain.model.catalog.ItemId;
import java.time.Instant;

/** The classification rules set the automatic tags and collections of a file (ADR 0016). */
public final class FileClassified extends CatalogEvent {

  public FileClassified(ItemId itemId, Instant occurredAt) {
    super(itemId, occurredAt);
  }

  public FileClassified(EventId id, Instant occurredAt, ItemId itemId) {
    super(id, occurredAt, itemId);
  }

  @Override
  public ChangeType changeType() {
    return ChangeType.MODIFIED;
  }
}
