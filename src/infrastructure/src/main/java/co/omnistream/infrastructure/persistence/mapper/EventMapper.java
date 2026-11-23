package co.omnistream.infrastructure.persistence.mapper;

import co.omnistream.domain.event.CatalogEvent;
import co.omnistream.domain.event.CollectionCreated;
import co.omnistream.domain.event.CollectionModified;
import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.event.EventId;
import co.omnistream.domain.event.FileClassified;
import co.omnistream.domain.event.FileDeleted;
import co.omnistream.domain.event.FileIngested;
import co.omnistream.domain.event.FileModified;
import co.omnistream.domain.model.catalog.ItemId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/** Reads a row of {@code outbox} or {@code change_log} as a domain event (Data Mapper). */
public final class EventMapper {

  /** The event of the current row. */
  public DomainEvent toEvent(ResultSet row) throws SQLException {
    EventId id = new EventId(UUID.fromString(row.getString("event_id")));
    Instant occurredAt = Instant.ofEpochMilli(row.getLong("occurred_at"));
    ItemId itemId = new ItemId(UUID.fromString(row.getString("item_id")));
    String type = row.getString("event_type");
    return switch (type) {
      case "FileIngested" -> new FileIngested(id, occurredAt, itemId);
      case "FileModified" -> new FileModified(id, occurredAt, itemId);
      case "FileDeleted" -> new FileDeleted(id, occurredAt, itemId);
      case "FileClassified" -> new FileClassified(id, occurredAt, itemId);
      case "CollectionCreated" -> new CollectionCreated(id, occurredAt, itemId);
      case "CollectionModified" -> new CollectionModified(id, occurredAt, itemId);
      default -> throw new SQLException("Unknown event type in the outbox: " + type);
    };
  }

  /**
   * The event as a {@link CatalogEvent}; the outbox and the change log only store catalog events.
   */
  public CatalogEvent toCatalogEvent(ResultSet row) throws SQLException {
    return (CatalogEvent) toEvent(row);
  }
}
