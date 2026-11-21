package co.omnistream.domain.port.dto;

import co.omnistream.domain.event.CatalogEvent;
import co.omnistream.domain.event.ChangeType;
import co.omnistream.domain.model.catalog.ItemId;
import java.time.Instant;
import java.util.Objects;

/**
 * A change to the catalog, as the live feed tells it to the people looking at it (ADR 0026): which
 * file or collection, whether it was added, modified or removed, and the event behind it.
 *
 * @param item the file or collection that changed
 * @param change whether it was added, modified or removed
 * @param event the name of the domain event, such as {@code FileModified} or {@code
 *     CollectionCreated}
 * @param at when it happened
 */
public record CatalogChange(ItemId item, ChangeType change, String event, Instant at) {
  public CatalogChange {
    Objects.requireNonNull(item, "item");
    Objects.requireNonNull(change, "change");
    Objects.requireNonNull(event, "event");
    Objects.requireNonNull(at, "at");
  }

  /** The change {@code e} describes. */
  public static CatalogChange of(CatalogEvent e) {
    return new CatalogChange(e.item(), e.changeType(), e.type(), e.occurredAt());
  }
}
