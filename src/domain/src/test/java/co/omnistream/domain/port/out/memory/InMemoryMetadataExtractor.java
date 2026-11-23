package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.port.out.IMetadataExtractor;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * In-memory metadata extractor for the domain tests (ADR 0018).
 *
 * <p>It returns the entries it was configured with, and supports the media types it was given.
 */
public final class InMemoryMetadataExtractor implements IMetadataExtractor {

  private final List<MetadataEntry> entries;
  private final Set<MediaType> supported;

  /** An extractor that supports every type and returns no metadata. */
  public InMemoryMetadataExtractor() {
    this(List.of(), MediaType.values());
  }

  public InMemoryMetadataExtractor(List<MetadataEntry> entries, MediaType... supported) {
    this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    this.supported = Set.of(supported);
  }

  @Override
  public boolean supports(MediaType type) {
    return supported.contains(Objects.requireNonNull(type, "type"));
  }

  @Override
  public List<MetadataEntry> extract(Path path) {
    Objects.requireNonNull(path, "path");
    return entries;
  }
}
