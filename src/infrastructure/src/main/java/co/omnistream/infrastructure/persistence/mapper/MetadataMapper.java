package co.omnistream.infrastructure.persistence.mapper;

import co.omnistream.domain.model.catalog.MetadataEntry;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Reads a row of {@code file_metadata} (Data Mapper). */
public final class MetadataMapper {

  /** The metadata entry of the current row. */
  public MetadataEntry toValue(ResultSet row) throws SQLException {
    return new MetadataEntry(
        row.getString("entry_key"),
        row.getString("entry_value"),
        co.omnistream.domain.model.catalog.MetadataSource.valueOf(row.getString("source")));
  }
}
