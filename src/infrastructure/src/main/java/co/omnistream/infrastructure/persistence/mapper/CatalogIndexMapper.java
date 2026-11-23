package co.omnistream.infrastructure.persistence.mapper;

import co.omnistream.domain.model.catalog.CatalogId;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/** Turns the catalog row and the rows it owns into a {@link CatalogSnapshot} (Data Mapper). */
public final class CatalogIndexMapper {

  /** The catalog with the files and collections loaded from its own tables. */
  public CatalogSnapshot toSnapshot(
      ResultSet row, List<MediaFileSnapshot> files, List<CollectionSnapshot> collections)
      throws SQLException {
    return new CatalogSnapshot(
        new CatalogId(UUID.fromString(row.getString("id"))),
        row.getString("name"),
        row.getLong("version"),
        files,
        collections);
  }

  /** The identifier of the catalog stored in the database, if there is one. */
  public CatalogSnapshot empty() {
    return new CatalogSnapshot(
        new CatalogId(UUID.randomUUID()), "OmniStream", 0, List.of(), List.of());
  }
}
