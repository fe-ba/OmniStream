package co.omnistream.domain.model.catalog;

import co.omnistream.domain.event.CatalogEvent;
import co.omnistream.domain.event.CollectionCreated;
import co.omnistream.domain.event.CollectionModified;
import co.omnistream.domain.event.FileClassified;
import co.omnistream.domain.event.FileDeleted;
import co.omnistream.domain.event.FileIngested;
import co.omnistream.domain.event.FileModified;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.policy.DeduplicationPolicy;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root of the catalog: every file and collection of the library, and the only way to
 * change them. Each method that changes the catalog records an event, which the persistence adapter
 * writes to the outbox with the aggregate ({@link #pullEvents()}).
 *
 * <p>It is the originator of {@link CatalogSnapshot} (Memento, ADR 0011). {@code version} is the
 * one it was loaded with: the repository compares and increments it on save (optimistic locking).
 */
public final class CatalogIndex {

  private static final DeduplicationPolicy DEDUPLICATION = new DeduplicationPolicy();

  private final CatalogId id;
  private final String name;
  private final Map<ItemId, MediaFile> files;
  private final List<Collection> collections;
  private final List<CatalogEvent> events;
  private final long version;

  private CatalogIndex(CatalogId id, String name, long version) {
    this.id = Objects.requireNonNull(id, "id");
    this.name = Objects.requireNonNull(name, "name");
    this.version = version;
    this.files = new LinkedHashMap<>();
    this.collections = new ArrayList<>();
    this.events = new ArrayList<>();
  }

  /**
   * Adds a file and marks it {@link IndexingStatus#INDEXED}. If a file with the same content is
   * already in the catalog, the {@link DeduplicationPolicy} decides what happens.
   *
   * @return what happened; {@link IngestionResult#wasIndexed()} is false when the file was rejected
   *     as a duplicate
   */
  public IngestionResult ingest(MediaFile file, Instant now) {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(now, "now");
    if (files.containsKey(file.id)) {
      throw new IllegalArgumentException("The file is already in the catalog: " + file.id.value());
    }
    Optional<MediaFile> existing = files.values().stream().filter(file::isDuplicateOf).findFirst();
    if (existing.isEmpty()) {
      index(file, now);
      return new IngestionResult(file.id, false, Optional.empty());
    }
    DuplicateAction action = DEDUPLICATION.resolve(file, existing.get());
    switch (action) {
      case REJECT -> {
        return new IngestionResult(existing.get().id, true, Optional.of(action));
      }
      case REPLACE -> replace(existing.get(), file, now);
      case KEEP_BOTH -> index(file, now);
    }
    return new IngestionResult(file.id, true, Optional.of(action));
  }

  /** The first file with that content, if there is one. */
  public Optional<MediaFile> findByFingerprint(Fingerprint h) {
    Objects.requireNonNull(h, "h");
    return files.values().stream().filter(file -> file.fingerprint().matches(h)).findFirst();
  }

  /** The file or collection with that identifier, if there is one. */
  public Optional<CatalogItem> findItem(ItemId id) {
    Objects.requireNonNull(id, "id");
    if (files.containsKey(id)) {
      return Optional.of(files.get(id));
    }
    return collections.stream().filter(c -> c.id.equals(id)).map(c -> (CatalogItem) c).findFirst();
  }

  /** Whether a file with that content is already in the catalog. */
  public boolean hasDuplicate(Fingerprint h) {
    return findByFingerprint(h).isPresent();
  }

  /**
   * Sets a metadata value of a file on behalf of a user. The value is stored as {@link
   * MetadataSource#MANUAL}, so a later extraction does not overwrite it.
   *
   * @throws ResourceNotFound if there is no file with that identifier
   */
  public void editMetadata(ItemId id, MetadataEntry m, Instant now) {
    Objects.requireNonNull(m, "m");
    Objects.requireNonNull(now, "now");
    MediaFile file = requireFile(id);
    file.addMetadata(m.isManual() ? m : m.withValue(m.value()));
    register(new FileModified(file.id, now));
  }

  /**
   * Adds a tag to a file (see {@link MediaFile#tag(Tag)}).
   *
   * @throws ResourceNotFound if there is no file with that identifier
   */
  public void tag(ItemId id, Tag e, Instant now) {
    Objects.requireNonNull(e, "e");
    Objects.requireNonNull(now, "now");
    MediaFile file = requireFile(id);
    file.tag(e);
    register(new FileModified(file.id, now));
  }

  /**
   * Applies the result of the classification rules to a file (ADR 0016): replaces its automatic
   * tags and adds it to the given collections. Manual tags are left untouched.
   *
   * @throws ResourceNotFound if the file or one of the collections does not exist
   */
  public void applyClassification(ItemId id, Classification c, Instant now) {
    Objects.requireNonNull(c, "c");
    Objects.requireNonNull(now, "now");
    MediaFile file = requireFile(id);
    List<Collection> targets = c.collections().stream().map(this::requireCollection).toList();
    file.replaceAutomaticTags(c.tags());
    register(new FileClassified(file.id, now));
    for (Collection target : targets) {
      if (!target.hasItem(file)) {
        target.add(file);
        register(new CollectionModified(target.id, now));
      }
    }
  }

  /**
   * Records that the content of a file was replaced (ADR 0027): its new fingerprint and size. The
   * same content again records nothing. The new content is not checked for duplicates: that only
   * happens when a file enters the catalog.
   *
   * @throws ResourceNotFound if there is no file with that identifier
   */
  public void replaceContent(
      ItemId id, Fingerprint newFingerprint, long newSizeBytes, Instant now) {
    Objects.requireNonNull(now, "now");
    MediaFile file = requireFile(id);
    if (file.replaceContent(newFingerprint, newSizeBytes)) {
      register(new FileModified(file.id, now));
    }
  }

  /**
   * Removes a file from the catalog and from every collection that holds it. Only files are
   * deleted: version 1.0 has no use case for deleting collections.
   *
   * @throws ResourceNotFound if there is no file with that identifier
   */
  public void delete(ItemId id, Instant now) {
    Objects.requireNonNull(now, "now");
    MediaFile file = requireFile(id);
    files.remove(file.id);
    register(new FileDeleted(file.id, now));
    for (Collection collection : collections) {
      if (collection.hasItem(file)) {
        collection.remove(file);
        register(new CollectionModified(collection.id, now));
      }
    }
  }

  /** Creates an empty collection at the top level of the catalog. */
  public Collection createCollection(String name, Instant now) {
    Objects.requireNonNull(now, "now");
    Collection collection = Collection.create(name);
    collections.add(collection);
    register(new CollectionCreated(collection.id, now));
    return collection;
  }

  /**
   * Puts a file or a collection inside a collection. Adding an item that is already there does
   * nothing.
   *
   * @throws ResourceNotFound if the collection or the item does not exist
   * @throws co.omnistream.domain.exception.CyclicCollection if the collection would contain itself
   */
  public void addToCollection(ItemId collection, ItemId item, Instant now) {
    Objects.requireNonNull(now, "now");
    Collection target = requireCollection(collection);
    CatalogItem member =
        findItem(item).orElseThrow(() -> new ResourceNotFound("No item " + item.value()));
    if (!target.hasItem(member)) {
      target.add(member);
      register(new CollectionModified(target.id, now));
    }
  }

  /** Number of files in the catalog. */
  public int totalFiles() {
    return files.size();
  }

  /** Returns the events recorded since the last call, and forgets them. */
  public List<CatalogEvent> pullEvents() {
    List<CatalogEvent> pulled = List.copyOf(events);
    events.clear();
    return pulled;
  }

  /** State of the catalog (Memento, ADR 0011). */
  public CatalogSnapshot toSnapshot() {
    return new CatalogSnapshot(
        id,
        name,
        version,
        files.values().stream().map(MediaFile::toSnapshot).toList(),
        collections.stream().map(Collection::toSnapshot).toList());
  }

  /**
   * Rebuilds a catalog without validating its rules and without recording events (Memento, ADR
   * 0011). An empty snapshot with version 0 gives a new, empty catalog.
   *
   * @throws IllegalArgumentException if a collection refers to an item that is not in the snapshot
   */
  public static CatalogIndex fromSnapshot(CatalogSnapshot i) {
    Objects.requireNonNull(i, "i");
    CatalogIndex catalog = new CatalogIndex(i.id(), i.name(), i.version());
    for (MediaFileSnapshot fileSnapshot : i.files()) {
      MediaFile file = MediaFile.restore(fileSnapshot);
      catalog.files.put(file.id, file);
    }
    for (CollectionSnapshot collectionSnapshot : i.collections()) {
      catalog.collections.add(Collection.restore(collectionSnapshot));
    }
    for (CollectionSnapshot collectionSnapshot : i.collections()) {
      Collection collection = catalog.requireCollection(collectionSnapshot.id());
      for (ItemId itemId : collectionSnapshot.items()) {
        CatalogItem item =
            catalog
                .findItem(itemId)
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "Collection "
                            + collectionSnapshot.id().value()
                            + " refers to an unknown item "
                            + itemId.value()));
        collection.restoreItem(item);
      }
    }
    return catalog;
  }

  private void register(CatalogEvent e) {
    events.add(e);
  }

  private void index(MediaFile file, Instant now) {
    file.markIndexed();
    files.put(file.id, file);
    register(new FileIngested(file.id, now));
  }

  /** The incoming file takes the place of the existing one, also inside collections. */
  private void replace(MediaFile existing, MediaFile incoming, Instant now) {
    files.remove(existing.id);
    register(new FileDeleted(existing.id, now));
    index(incoming, now);
    for (Collection collection : collections) {
      if (collection.hasItem(existing)) {
        collection.replaceItem(existing, incoming);
        register(new CollectionModified(collection.id, now));
      }
    }
  }

  private MediaFile requireFile(ItemId id) {
    Objects.requireNonNull(id, "id");
    MediaFile file = files.get(id);
    if (file == null) {
      throw new ResourceNotFound("No file " + id.value());
    }
    return file;
  }

  private Collection requireCollection(ItemId id) {
    Objects.requireNonNull(id, "id");
    return collections.stream()
        .filter(c -> c.id.equals(id))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFound("No collection " + id.value()));
  }
}
