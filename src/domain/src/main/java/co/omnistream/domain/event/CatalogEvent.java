package co.omnistream.domain.event;

import co.omnistream.domain.model.catalog.ItemId;
import java.time.Instant;
import java.util.Objects;

/**
 * Change to one item of the catalog, recorded by {@code CatalogIndex}.
 *
 * <p>{@link #changeType()} tells the change log whether the item was added, modified or removed, so
 * clients can synchronize.
 */
public abstract class CatalogEvent extends DomainEvent {

  private final ItemId itemId;

  /** A new event, with a new identifier. */
  protected CatalogEvent(ItemId itemId, Instant occurredAt) {
    this(EventId.random(), occurredAt, itemId);
  }

  /** An event with a known identifier, for example when read from the outbox. */
  protected CatalogEvent(EventId id, Instant occurredAt, ItemId itemId) {
    super(id, occurredAt);
    this.itemId = Objects.requireNonNull(itemId, "itemId");
  }

  /** The file or collection that changed. */
  public ItemId item() {
    return itemId;
  }

  /** Kind of change, for the change log. */
  public abstract ChangeType changeType();
}
