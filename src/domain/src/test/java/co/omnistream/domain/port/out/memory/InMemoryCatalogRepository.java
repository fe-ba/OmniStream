package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.event.CatalogEvent;
import co.omnistream.domain.exception.VersionConflict;
import co.omnistream.domain.model.catalog.CatalogId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.port.dto.SearchCriteria;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory catalog repository for the domain tests (ADR 0018).
 *
 * <p>It keeps a snapshot and applies the same optimistic locking as the real adapter. When it is
 * given an {@link InMemoryOutboxRepository}, saving the catalog also writes the events it pulled,
 * so tests see the transactional-outbox behaviour of the JDBC adapter.
 */
public final class InMemoryCatalogRepository implements ICatalogRepository {

  private static final String DEFAULT_NAME = "OmniStream";

  private final InMemoryOutboxRepository outbox;
  private CatalogSnapshot stored;

  /** A repository whose saves discard the pulled events, because there is no outbox. */
  public InMemoryCatalogRepository() {
    this(null);
  }

  /** A repository that writes the pulled events to {@code outbox} on every save. */
  public InMemoryCatalogRepository(InMemoryOutboxRepository outbox) {
    this.outbox = outbox;
  }

  @Override
  public CatalogIndex getCatalog() {
    if (stored == null) {
      return CatalogIndex.fromSnapshot(
          new CatalogSnapshot(
              new CatalogId(UUID.randomUUID()), DEFAULT_NAME, 0, List.of(), List.of()));
    }
    return CatalogIndex.fromSnapshot(stored);
  }

  @Override
  public void save(CatalogIndex catalog) {
    Objects.requireNonNull(catalog, "catalog");
    CatalogSnapshot snapshot = catalog.toSnapshot();
    long storedVersion = stored == null ? 0 : stored.version();
    if (snapshot.version() != storedVersion) {
      throw new VersionConflict(
          "The catalog changed: stored version is "
              + storedVersion
              + ", loaded version was "
              + snapshot.version());
    }
    List<CatalogEvent> events = catalog.pullEvents();
    stored =
        new CatalogSnapshot(
            snapshot.id(),
            snapshot.name(),
            storedVersion + 1,
            snapshot.files(),
            snapshot.collections());
    if (outbox != null) {
      outbox.addAll(events);
    }
  }

  @Override
  public Optional<MediaFileSnapshot> findFile(ItemId id) {
    Objects.requireNonNull(id, "id");
    return files().stream().filter(file -> file.id().equals(id)).findFirst();
  }

  @Override
  public Optional<MediaFileSnapshot> findByFingerprint(Fingerprint fingerprint) {
    Objects.requireNonNull(fingerprint, "fingerprint");
    return files().stream().filter(file -> file.fingerprint().matches(fingerprint)).findFirst();
  }

  @Override
  public List<MediaFileSnapshot> findByText(String text) {
    Objects.requireNonNull(text, "text");
    String wanted = text.strip().toLowerCase(Locale.ROOT);
    if (wanted.isEmpty()) {
      return List.of();
    }
    return sorted(files().stream().filter(file -> matchesText(file, wanted)).toList());
  }

  @Override
  public List<MediaFileSnapshot> findByCriteria(SearchCriteria criteria) {
    Objects.requireNonNull(criteria, "criteria");
    String text = criteria.text().map(value -> value.strip().toLowerCase(Locale.ROOT)).orElse("");
    return sorted(
        files().stream()
            .filter(file -> text.isEmpty() || matchesText(file, text))
            .filter(file -> criteria.type().map(type -> file.type() == type).orElse(true))
            .filter(file -> hasAllTags(file, criteria.tags()))
            .toList());
  }

  private List<MediaFileSnapshot> files() {
    return stored == null ? List.of() : stored.files();
  }

  private static List<MediaFileSnapshot> sorted(List<MediaFileSnapshot> files) {
    return files.stream().sorted(Comparator.comparing(MediaFileSnapshot::name)).toList();
  }

  private static boolean matchesText(MediaFileSnapshot file, String wanted) {
    if (file.name().toLowerCase(Locale.ROOT).contains(wanted)) {
      return true;
    }
    if (file.metadata().stream()
        .anyMatch(entry ->
            entry.key().toLowerCase(Locale.ROOT).contains(wanted)
                || entry.value().toLowerCase(Locale.ROOT).contains(wanted))) {
      return true;
    }
    return file.tags().stream().anyMatch(tag -> tag.normalized().contains(wanted));
  }

  private static boolean hasAllTags(MediaFileSnapshot file, Set<Tag> wanted) {
    return wanted.stream()
        .allMatch(expected ->
            file.tags().stream().anyMatch(tag -> tag.normalized().equals(expected.normalized())));
  }
}
