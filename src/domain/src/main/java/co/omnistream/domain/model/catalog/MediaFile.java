package co.omnistream.domain.model.catalog;

import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Leaf of the catalog tree (Composite): one video, audio, image or document.
 *
 * <p>Two rules protect what users write. A manual metadata value is never overwritten by an
 * extracted one, and a manual tag is never replaced by an automatic one with the same name.
 */
public final class MediaFile extends CatalogItem {

  private final Path path;
  private final Fingerprint fingerprint;
  private final MediaType type;
  private final long sizeBytes;
  private final Duration duration;
  private IndexingStatus status;
  private final Instant ingestedAt;
  private final List<MetadataEntry> metadata;
  private final Set<Tag> tags;

  private MediaFile(
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
      Set<Tag> tags) {
    super(id, name);
    this.path = Objects.requireNonNull(path, "path");
    this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
    this.type = Objects.requireNonNull(type, "type");
    this.sizeBytes = sizeBytes;
    this.duration = Objects.requireNonNull(duration, "duration");
    this.status = Objects.requireNonNull(status, "status");
    this.ingestedAt = Objects.requireNonNull(ingestedAt, "ingestedAt");
    this.metadata = new ArrayList<>(metadata);
    this.tags = new LinkedHashSet<>(tags);
  }

  /**
   * A new file, {@link IndexingStatus#PENDING} until the catalog indexes it.
   *
   * @param duration length of the content; {@link Duration#ZERO} for images and documents
   */
  public static MediaFile create(
      String name,
      Path path,
      Fingerprint fingerprint,
      MediaType type,
      long sizeBytes,
      Duration duration,
      Instant now) {
    if (sizeBytes < 0) {
      throw new IllegalArgumentException("A file cannot have a negative size");
    }
    if (Objects.requireNonNull(duration, "duration").isNegative()) {
      throw new IllegalArgumentException("A file cannot have a negative duration");
    }
    return new MediaFile(
        new ItemId(UUID.randomUUID()),
        name,
        path,
        fingerprint,
        type,
        sizeBytes,
        duration,
        IndexingStatus.PENDING,
        now,
        List.of(),
        Set.of());
  }

  /** Rebuilds a file from its snapshot, without validating (Memento, ADR 0011). */
  static MediaFile restore(MediaFileSnapshot s) {
    return new MediaFile(
        s.id(),
        s.name(),
        s.path(),
        s.fingerprint(),
        s.type(),
        s.sizeBytes(),
        s.duration(),
        s.status(),
        s.ingestedAt(),
        s.metadata(),
        s.tags());
  }

  /** Whether {@code other} is a different file with the same content. */
  public boolean isDuplicateOf(MediaFile other) {
    Objects.requireNonNull(other, "other");
    return !id.equals(other.id) && fingerprint.matches(other.fingerprint);
  }

  /**
   * Sets a metadata value, replacing the entry with the same key. An extracted value does not
   * replace a manual one.
   */
  public void addMetadata(MetadataEntry m) {
    Objects.requireNonNull(m, "m");
    Optional<MetadataEntry> current = metadataEntry(m.key());
    if (current.isEmpty()) {
      metadata.add(m);
    } else if (m.isManual() || !current.get().isManual()) {
      metadata.set(metadata.indexOf(current.get()), m);
    }
  }

  /** The metadata entry with that key, if there is one. */
  public Optional<MetadataEntry> metadataEntry(String key) {
    String wanted = Objects.requireNonNull(key, "key").strip();
    return metadata.stream().filter(entry -> entry.key().equals(wanted)).findFirst();
  }

  /**
   * Adds a tag, unless the file already has one with the same {@link Tag#normalized() normalized}
   * name. A manual tag takes the place of an automatic one with that name; an automatic tag never
   * replaces a manual one.
   */
  public void tag(Tag e) {
    Objects.requireNonNull(e, "e");
    Optional<Tag> sameName =
        tags.stream().filter(t -> t.normalized().equals(e.normalized())).findFirst();
    if (sameName.isEmpty()) {
      tags.add(e);
    } else if (sameName.get().isAutomatic() && !e.isAutomatic()) {
      tags.remove(sameName.get());
      tags.add(e);
    }
  }

  /**
   * Replaces every automatic tag with {@code newTags}, which must all be automatic (ADR 0016).
   * Manual tags stay as they are.
   */
  public void replaceAutomaticTags(Set<Tag> newTags) {
    for (Tag tag : newTags) {
      if (!tag.isAutomatic()) {
        throw new IllegalArgumentException("Only automatic tags can be replaced: " + tag.name());
      }
    }
    tags.removeIf(Tag::isAutomatic);
    newTags.forEach(this::tag);
  }

  /** The file is processed and searchable. */
  public void markIndexed() {
    status = IndexingStatus.INDEXED;
  }

  /**
   * Processing failed. The file stays in the catalog so it can be indexed again; the cause is
   * reported by the service, the file only keeps its status.
   */
  public void markFailed(String cause) {
    Objects.requireNonNull(cause, "cause");
    status = IndexingStatus.ERROR;
  }

  @Override
  public long totalSize() {
    return sizeBytes;
  }

  @Override
  public int countFiles() {
    return 1;
  }

  @Override
  public boolean contains(CatalogItem e) {
    Objects.requireNonNull(e, "e");
    return false;
  }

  @Override
  void collectFiles(Set<MediaFile> into) {
    into.add(this);
  }

  @Override
  public MediaFileSnapshot toSnapshot() {
    return new MediaFileSnapshot(
        id, name, path, fingerprint, type, sizeBytes, duration, status, ingestedAt, metadata, tags);
  }

  Fingerprint fingerprint() {
    return fingerprint;
  }
}
