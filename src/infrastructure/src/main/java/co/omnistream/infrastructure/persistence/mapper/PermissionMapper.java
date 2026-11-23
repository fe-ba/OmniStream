package co.omnistream.infrastructure.persistence.mapper;

import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.catalog.ItemId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Reads a row of {@code user_permissions} (Data Mapper). */
public final class PermissionMapper {

  /** The permission of the current row. */
  public Permission toValue(ResultSet row) throws SQLException {
    return new Permission(
        new ItemId(UUID.fromString(row.getString("resource_id"))),
        PermissionLevel.valueOf(row.getString("level")));
  }
}
