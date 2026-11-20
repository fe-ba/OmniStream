package co.omnistream.domain.snapshot;

import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.IndexingStatus;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.model.catalog.Tag;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * State of a {@code MediaFile} (Memento, ADR 0011).
 *
 * @param id the file
 * @param name name shown to users
 * @param path where the file is stored
 * @param fingerprint digest of its content
 * @param type kind of content
 * @param sizeBytes size in bytes
 * @param duration length; zero for images and documents
 * @param status where it is in the ingestion process
 * @param ingestedAt when it entered the catalog
 * @param metadata its metadata entries
 * @param tags its tags
 */
public record MediaFileSnapshot(
    ItemId id,
    String name,
    Path path,
    Fingerprint fingerprint,
    MediaType type,
    long sizeBytes,
    Duration duration,
    IndexingStatus status,
    Instant ingestedAt,
    List<MetadataEntry> metadata,
    Set<Tag> tags)
    implements CatalogItemSnapshot {

  public MediaFileSnapshot {
    metadata = List.copyOf(metadata);
    tags = Set.copyOf(tags);
  }
}
