package co.omnistream.infrastructure.persistence.mapper;

import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.IndexingStatus;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.model.catalog.MetadataSource;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.model.catalog.TagSource;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Reads a file row and its metadata and tags (Data Mapper). */
public final class MediaFileMapper {

  /** The file, with the metadata and tags loaded from their tables. */
  public MediaFileSnapshot toSnapshot(ResultSet row, List<MetadataEntry> metadata, Set<Tag> tags)
      throws SQLException {
    return new MediaFileSnapshot(
        new ItemId(UUID.fromString(row.getString("id"))),
        row.getString("name"),
        Path.of(row.getString("path")),
        new Fingerprint(row.getString("fingerprint_algorithm"), row.getString("fingerprint_value")),
        MediaType.valueOf(row.getString("type")),
        row.getLong("size_bytes"),
        Duration.ofNanos(row.getLong("duration_nanos")),
        IndexingStatus.valueOf(row.getString("status")),
        Instant.ofEpochMilli(row.getLong("ingested_at")),
        metadata,
        tags);
  }

  /** A metadata row as the domain value. */
  public MetadataEntry metadata(ResultSet row) throws SQLException {
    return new MetadataEntry(
        row.getString("entry_key"),
        row.getString("entry_value"),
        MetadataSource.valueOf(row.getString("source")));
  }

  /** A tag row as the domain value. */
  public Tag tag(ResultSet row) throws SQLException {
    return new Tag(row.getString("name"), TagSource.valueOf(row.getString("source")));
  }
}
