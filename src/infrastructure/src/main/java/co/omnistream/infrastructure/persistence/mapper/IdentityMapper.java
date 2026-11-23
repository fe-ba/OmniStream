package co.omnistream.infrastructure.persistence.mapper;

import co.omnistream.domain.model.access.LinkedIdentity;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Reads a row of {@code user_identities} (Data Mapper). */
public final class IdentityMapper {

  /** The linked identity of the current row. */
  public LinkedIdentity toValue(ResultSet row) throws SQLException {
    return new LinkedIdentity(row.getString("issuer"), row.getString("subject"));
  }
}
