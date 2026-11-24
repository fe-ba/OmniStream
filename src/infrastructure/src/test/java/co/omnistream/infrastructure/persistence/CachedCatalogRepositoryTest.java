package co.omnistream.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.catalog.CatalogId;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CachedCatalogRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  @Test
  void answersFromTheCacheWhileItIsFresh() {
    CountingRepository delegate = new CountingRepository();
    CachedCatalogRepository repository =
        new CachedCatalogRepository(
            delegate, Duration.ofSeconds(30), Clock.fixed(NOW, ZoneOffset.UTC));

    repository.getCatalog();
    repository.getCatalog();

    assertThat(delegate.reads()).isEqualTo(1);
  }

  @Test
  void readsAgainAfterTheTimeToLive() {
    MutableClock clock = new MutableClock(NOW);
    CountingRepository delegate = new CountingRepository();
    CachedCatalogRepository repository =
        new CachedCatalogRepository(delegate, Duration.ofSeconds(30), clock);
    repository.getCatalog();

    clock.advance(Duration.ofSeconds(31));
    repository.getCatalog();

    assertThat(delegate.reads()).isEqualTo(2);
  }

  @Test
  void dropsTheCacheWhenTheCatalogIsSaved() {
    CountingRepository delegate = new CountingRepository();
    CachedCatalogRepository repository =
        new CachedCatalogRepository(
            delegate, Duration.ofSeconds(30), Clock.fixed(NOW, ZoneOffset.UTC));
    CatalogIndex catalog = repository.getCatalog();

    repository.save(catalog);
    repository.getCatalog();

    assertThat(delegate.reads()).isEqualTo(2);
  }

  @Test
  void eachReaderGetsACatalogOfItsOwn() {
    CachedCatalogRepository repository =
        new CachedCatalogRepository(
            new CountingRepository(), Duration.ofSeconds(30), Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(repository.getCatalog()).isNotSameAs(repository.getCatalog());
  }

  @Test
  void aChangeThatIsNotSavedDoesNotReachTheNextReader() {
    CountingRepository delegate = new CountingRepository();
    CachedCatalogRepository repository =
        new CachedCatalogRepository(
            delegate, Duration.ofSeconds(30), Clock.fixed(NOW, ZoneOffset.UTC));

    repository.getCatalog().createCollection("Never saved", NOW);

    assertThat(repository.getCatalog().toSnapshot().collections()).isEmpty();
    assertThat(delegate.reads()).isEqualTo(1);
  }

  private static CatalogIndex catalog() {
    return CatalogIndex.fromSnapshot(
        new CatalogSnapshot(
            new CatalogId(UUID.randomUUID()), "OmniStream", 0, List.of(), List.of()));
  }

  private static final class CountingRepository implements ICatalogRepository {
    private final CatalogIndex catalog = catalog();
    private final AtomicInteger reads = new AtomicInteger();
    private int writes;

    int reads() {
      return reads.get();
    }

    @Override
    public CatalogIndex getCatalog() {
      reads.incrementAndGet();
      return catalog;
    }

    @Override
    public void save(CatalogIndex c) {
      writes++;
    }

    @Override
    public Optional<MediaFileSnapshot> findFile(ItemId id) {
      return Optional.empty();
    }

    @Override
    public Optional<MediaFileSnapshot> findByFingerprint(Fingerprint h) {
      return Optional.empty();
    }

    @Override
    public List<MediaFileSnapshot> findByText(String text) {
      return List.of();
    }

    @Override
    public List<MediaFileSnapshot> findByCriteria(SearchCriteria c) {
      return List.of();
    }
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    private MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
