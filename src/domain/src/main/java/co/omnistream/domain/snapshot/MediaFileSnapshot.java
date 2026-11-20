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

/** Immutable snapshot of an aggregate's state (Memento, ADR 0011). */
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
    implements CatalogItemSnapshot {}
