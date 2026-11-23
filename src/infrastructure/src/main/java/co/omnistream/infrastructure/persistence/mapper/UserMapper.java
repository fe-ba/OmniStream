package co.omnistream.infrastructure.persistence.mapper;

import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.snapshot.UserSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

/** Reads a user row with its permissions and identities (Data Mapper). */
public final class UserMapper {

  /** The user, with the rows loaded from their tables. */
  public UserSnapshot toSnapshot(
      ResultSet row, Set<Permission> permissions, Set<LinkedIdentity> identities)
      throws SQLException {
    return new UserSnapshot(
        new UserId(UUID.fromString(row.getString("id"))),
        row.getString("username"),
        Role.valueOf(row.getString("role")),
        permissions,
        identities,
        row.getInt("active") != 0);
  }
}
