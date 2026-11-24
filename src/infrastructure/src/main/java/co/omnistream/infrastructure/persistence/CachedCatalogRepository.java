package co.omnistream.infrastructure.persistence;

import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.SearchCriteria;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Decorator of the catalog repository that keeps the aggregate in memory for a while (Decorator,
 * ADR 0015).
 *
 * <p>Reading the whole catalog on every search is expensive; the catalog changes seldom and, when
 * it does, it is saved through this same decorator, which drops the cached copy. The time to live
 * is the second safeguard, for a change made by another process.
 *
 * <p>The cache keeps a snapshot, and every read rebuilds a catalog of its own from it. The
 * aggregate is mutable: if two requests shared one, a change that fails before it is saved would
 * stay in the cache, and two requests at once would change the same object.
 */
public final class CachedCatalogRepository implements ICatalogRepository {

  private final ICatalogRepository delegate;
  private final Duration ttl;
  private final Clock clock;

  private CatalogSnapshot cached;
  private Instant loadedAt;

  public CachedCatalogRepository(ICatalogRepository delegate, Duration ttl, Clock clock) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.ttl = Objects.requireNonNull(ttl, "ttl");
    this.clock = Objects.requireNonNull(clock, "clock");
    if (ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("The cache needs a positive time to live");
    }
  }

  /** A catalog of the caller's own, rebuilt from the cached snapshot. */
  @Override
  public synchronized CatalogIndex getCatalog() {
    if (cached == null || !fresh()) {
      cached = delegate.getCatalog().toSnapshot();
      loadedAt = clock.instant();
    }
    return CatalogIndex.fromSnapshot(cached);
  }

  @Override
  public synchronized void save(CatalogIndex c) {
    delegate.save(c);
    invalidate();
  }

  @Override
  public Optional<MediaFileSnapshot> findFile(ItemId id) {
    return delegate.findFile(id);
  }

  @Override
  public Optional<MediaFileSnapshot> findByFingerprint(Fingerprint h) {
    return delegate.findByFingerprint(h);
  }

  @Override
  public List<MediaFileSnapshot> findByText(String text) {
    return delegate.findByText(text);
  }

  @Override
  public List<MediaFileSnapshot> findByCriteria(SearchCriteria c) {
    return delegate.findByCriteria(c);
  }

  /** Drops the cached catalog: the next read comes from the database. */
  public synchronized void invalidate() {
    cached = null;
    loadedAt = null;
  }

  /** Whether the cached catalog is still within its time to live. */
  public synchronized boolean fresh() {
    return cached != null && loadedAt != null && loadedAt.plus(ttl).isAfter(clock.instant());
  }
}
