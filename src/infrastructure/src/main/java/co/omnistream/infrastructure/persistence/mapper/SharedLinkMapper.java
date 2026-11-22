package co.omnistream.infrastructure.persistence.mapper;

import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.sharing.SharedLinkId;
import co.omnistream.domain.snapshot.SharedLinkSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/** Reads a row of {@code shared_links} (Data Mapper). */
public final class SharedLinkMapper {

  /** The shared link of the current row. */
  public SharedLinkSnapshot toSnapshot(ResultSet row) throws SQLException {
    return new SharedLinkSnapshot(
        new SharedLinkId(UUID.fromString(row.getString("id"))),
        row.getString("token"),
        new ItemId(UUID.fromString(row.getString("collection_id"))),
        new UserId(UUID.fromString(row.getString("guest_id"))),
        PermissionLevel.valueOf(row.getString("level")),
        new UserId(UUID.fromString(row.getString("created_by"))),
        Instant.ofEpochMilli(row.getLong("expires_at")),
        row.getInt("revoked") != 0);
  }
}
