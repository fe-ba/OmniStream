package co.omnistream.infrastructure.persistence.mapper;

import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/** Reads a collection row and the identifiers of its members (Data Mapper). */
public final class CollectionMapper {

  /** The collection, with its members in their stored order. */
  public CollectionSnapshot toSnapshot(ResultSet row, List<ItemId> items) throws SQLException {
    return new CollectionSnapshot(
        new ItemId(UUID.fromString(row.getString("id"))),
        row.getString("name"),
        row.getString("description"),
        items);
  }
}
