package co.omnistream.domain.model.catalog;

import static co.omnistream.domain.model.catalog.CatalogFixtures.NOW;
import static co.omnistream.domain.model.catalog.CatalogFixtures.fingerprint;
import static co.omnistream.domain.model.catalog.CatalogFixtures.video;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.event.CatalogEvent;
import co.omnistream.domain.event.CollectionCreated;
import co.omnistream.domain.event.CollectionModified;
import co.omnistream.domain.event.FileClassified;
import co.omnistream.domain.event.FileDeleted;
import co.omnistream.domain.event.FileIngested;
import co.omnistream.domain.event.FileModified;
import co.omnistream.domain.exception.CyclicCollection;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogIndexTest {

  private final CatalogIndex catalog = emptyCatalog(3);

  // ---------------------------------------------------------------- ingestion

  @Test
  void ingestsANewFileAndMarksItIndexed() {
    MediaFile trip = video("Trip", "/library/trip.mp4", 'a');

    IngestionResult result = catalog.ingest(trip, NOW);

    assertThat(result).isEqualTo(new IngestionResult(id(trip), false, Optional.empty()));
    assertThat(catalog.totalFiles()).isEqualTo(1);
    assertThat(trip.toSnapshot().status()).isEqualTo(IndexingStatus.INDEXED);
    assertThat(eventTypes()).containsExactly(FileIngested.class);
  }

  @Test
  void rejectsTheSameFileSeenAgain() {
    MediaFile trip = video("Trip", "/library/trip.mp4", 'a');
    catalog.ingest(trip, NOW);
    catalog.pullEvents();

    IngestionResult result = catalog.ingest(video("Trip", "/library/trip.mp4", 'a'), NOW);

    assertThat(result.wasIndexed()).isFalse();
    assertThat(result.fileId()).isEqualTo(id(trip));
    assertThat(result.action()).contains(DuplicateAction.REJECT);
    assertThat(catalog.totalFiles()).isEqualTo(1);
    assertThat(catalog.pullEvents()).isEmpty();
  }

  @Test
  void keepsACopyFromAnotherPlace() {
    catalog.ingest(video("Trip", "/library/trip.mp4", 'a'), NOW);
    MediaFile copy = video("Trip", "/backup/trip.mp4", 'a');

    IngestionResult result = catalog.ingest(copy, NOW);

    assertThat(result)
        .isEqualTo(new IngestionResult(id(copy), true, Optional.of(DuplicateAction.KEEP_BOTH)));
    assertThat(catalog.totalFiles()).isEqualTo(2);
  }

  @Test
  void replacesAFileThatFailedToIndexAlsoInsideCollections() {
    MediaFile broken = video("Trip", "/library/trip.mp4", 'a');
    catalog.ingest(broken, NOW);
    Collection trips = catalog.createCollection("Trips", NOW);
    catalog.addToCollection(id(trips), id(broken), NOW);
    broken.markFailed("unreadable");
    catalog.pullEvents();
    MediaFile fixed = video("Trip", "/library/trip.mp4", 'a');

    IngestionResult result = catalog.ingest(fixed, NOW);

    assertThat(result.action()).contains(DuplicateAction.REPLACE);
    assertThat(catalog.findItem(id(broken))).isEmpty();
    assertThat(catalog.findItem(id(fixed))).isPresent();
    assertThat(trips.toSnapshot().items()).containsExactly(id(fixed));
    assertThat(eventTypes())
        .containsExactly(FileDeleted.class, FileIngested.class, CollectionModified.class);
  }

  @Test
  void theSameFileObjectCannotBeIngestedTwice() {
    MediaFile trip = video("Trip", "/library/trip.mp4", 'a');
    catalog.ingest(trip, NOW);

    assertThatThrownBy(() -> catalog.ingest(trip, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void findsFilesByContent() {
    MediaFile trip = video("Trip", "/library/trip.mp4", 'a');
    catalog.ingest(trip, NOW);

    assertThat(catalog.findByFingerprint(fingerprint('a'))).contains(trip);
    assertThat(catalog.hasDuplicate(fingerprint('a'))).isTrue();
    assertThat(catalog.hasDuplicate(fingerprint('b'))).isFalse();
  }

  @Test
  void findsFilesAndCollectionsById() {
    MediaFile trip = video("Trip", "/library/trip.mp4", 'a');
    catalog.ingest(trip, NOW);
    Collection trips = catalog.createCollection("Trips", NOW);

    assertThat(catalog.findItem(id(trip))).contains(trip);
    assertThat(catalog.findItem(id(trips))).contains(trips);
    assertThat(catalog.findItem(new ItemId(UUID.randomUUID()))).isEmpty();
  }

  // ---------------------------------------------------------------- metadata and tags

  @Test
  void anEditedMetadataValueIsStoredAsManual() {
    MediaFile trip = ingested("Trip", 'a');
    catalog.pullEvents();

    catalog.editMetadata(
        id(trip), new MetadataEntry("title", "Beach", MetadataSource.EXTRACTED), NOW);

    assertThat(trip.metadataEntry("title"))
        .contains(new MetadataEntry("title", "Beach", MetadataSource.MANUAL));
    assertThat(eventTypes()).containsExactly(FileModified.class);
  }

  @Test
  void editingAnUnknownFileFails() {
    ItemId unknown = new ItemId(UUID.randomUUID());
    MetadataEntry entry = new MetadataEntry("title", "x", MetadataSource.MANUAL);

    assertThatThrownBy(() -> catalog.editMetadata(unknown, entry, NOW))
        .isInstanceOf(ResourceNotFound.class);
  }

  @Test
  void tagsAFile() {
    MediaFile trip = ingested("Trip", 'a');
    catalog.pullEvents();

    catalog.tag(id(trip), new Tag("favorites", TagSource.MANUAL), NOW);

    assertThat(trip.toSnapshot().tags()).containsExactly(new Tag("favorites", TagSource.MANUAL));
    assertThat(eventTypes()).containsExactly(FileModified.class);
  }

  // ---------------------------------------------------------------- classification (ADR 0016)

  @Test
  void applyClassificationReplacesOnlyAutomaticTagsAndFillsCollections() {
    MediaFile trip = ingested("Trip", 'a');
    Collection videos = catalog.createCollection("Videos", NOW);
    catalog.tag(id(trip), new Tag("favorites", TagSource.MANUAL), NOW);
    trip.tag(new Tag("2025", TagSource.AUTOMATIC));
    catalog.pullEvents();
    Classification classification =
        new Classification(Set.of(new Tag("2026", TagSource.AUTOMATIC)), Set.of(id(videos)));

    catalog.applyClassification(id(trip), classification, NOW);

    assertThat(trip.toSnapshot().tags())
        .containsExactlyInAnyOrder(
            new Tag("favorites", TagSource.MANUAL), new Tag("2026", TagSource.AUTOMATIC));
    assertThat(videos.contains(trip)).isTrue();
    assertThat(eventTypes()).containsExactly(FileClassified.class, CollectionModified.class);
  }

  @Test
  void applyClassificationChangesNothingIfACollectionIsMissing() {
    MediaFile trip = ingested("Trip", 'a');
    catalog.pullEvents();
    Classification classification =
        new Classification(
            Set.of(new Tag("2026", TagSource.AUTOMATIC)), Set.of(new ItemId(UUID.randomUUID())));

    assertThatThrownBy(() -> catalog.applyClassification(id(trip), classification, NOW))
        .isInstanceOf(ResourceNotFound.class);
    assertThat(trip.toSnapshot().tags()).isEmpty();
    assertThat(catalog.pullEvents()).isEmpty();
  }

  // ---------------------------------------------------------------- deletion

  @Test
  void deletingAFileRemovesItFromItsCollections() {
    MediaFile trip = ingested("Trip", 'a');
    Collection trips = catalog.createCollection("Trips", NOW);
    catalog.addToCollection(id(trips), id(trip), NOW);
    catalog.pullEvents();

    catalog.delete(id(trip), NOW);

    assertThat(catalog.totalFiles()).isEqualTo(0);
    assertThat(trips.contains(trip)).isFalse();
    assertThat(eventTypes()).containsExactly(FileDeleted.class, CollectionModified.class);
  }

  @Test
  void collectionsAreNotDeleted() {
    Collection trips = catalog.createCollection("Trips", NOW);

    assertThatThrownBy(() -> catalog.delete(id(trips), NOW)).isInstanceOf(ResourceNotFound.class);
  }

  // ---------------------------------------------------------------- collections

  @Test
  void createsACollection() {
    Collection trips = catalog.createCollection("Trips", NOW);

    assertThat(catalog.findItem(id(trips))).contains(trips);
    assertThat(eventTypes()).containsExactly(CollectionCreated.class);
  }

  @Test
  void nestsCollections() {
    Collection trips = catalog.createCollection("Trips", NOW);
    Collection beach = catalog.createCollection("Beach", NOW);
    MediaFile sunset = ingested("Sunset", 'a');
    catalog.pullEvents();

    catalog.addToCollection(id(beach), id(sunset), NOW);
    catalog.addToCollection(id(trips), id(beach), NOW);

    assertThat(trips.contains(sunset)).isTrue();
    assertThat(eventTypes()).containsExactly(CollectionModified.class, CollectionModified.class);
  }

  @Test
  void addingAnItemAlreadyThereRecordsNothing() {
    Collection trips = catalog.createCollection("Trips", NOW);
    MediaFile sunset = ingested("Sunset", 'a');
    catalog.addToCollection(id(trips), id(sunset), NOW);
    catalog.pullEvents();

    catalog.addToCollection(id(trips), id(sunset), NOW);

    assertThat(catalog.pullEvents()).isEmpty();
  }

  @Test
  void refusesACycleBetweenCollections() {
    Collection trips = catalog.createCollection("Trips", NOW);
    Collection beach = catalog.createCollection("Beach", NOW);
    catalog.addToCollection(id(trips), id(beach), NOW);

    assertThatThrownBy(() -> catalog.addToCollection(id(beach), id(trips), NOW))
        .isInstanceOf(CyclicCollection.class);
  }

  @Test
  void addingToSomethingThatIsNotACollectionFails() {
    MediaFile trip = ingested("Trip", 'a');
    MediaFile other = ingested("Other", 'b');

    assertThatThrownBy(() -> catalog.addToCollection(id(trip), id(other), NOW))
        .isInstanceOf(ResourceNotFound.class);
  }

  // ---------------------------------------------------------------- events and snapshots

  @Test
  void pullEventsReturnsEachEventOnce() {
    ingested("Trip", 'a');

    assertThat(catalog.pullEvents()).hasSize(1);
    assertThat(catalog.pullEvents()).isEmpty();
  }

  @Test
  void eventsCarryTheItemAndTheTime() {
    MediaFile trip = ingested("Trip", 'a');

    CatalogEvent event = catalog.pullEvents().get(0);

    assertThat(event.item()).isEqualTo(id(trip));
    assertThat(event.occurredAt()).isEqualTo(NOW);
  }

  @Test
  void aSnapshotRebuildsTheSameCatalog() {
    MediaFile trip = ingested("Trip", 'a');
    MediaFile sunset = ingested("Sunset", 'b');
    catalog.tag(id(trip), new Tag("favorites", TagSource.MANUAL), NOW);
    Collection trips = catalog.createCollection("Trips", NOW);
    Collection beach = catalog.createCollection("Beach", NOW);
    catalog.addToCollection(id(beach), id(sunset), NOW);
    catalog.addToCollection(id(trips), id(beach), NOW);
    catalog.addToCollection(id(trips), id(trip), NOW);
    CatalogSnapshot snapshot = catalog.toSnapshot();

    CatalogIndex rebuilt = CatalogIndex.fromSnapshot(snapshot);

    assertThat(rebuilt.toSnapshot()).isEqualTo(snapshot);
    assertThat(rebuilt.pullEvents()).isEmpty();
    Collection rebuiltTrips = (Collection) rebuilt.findItem(id(trips)).orElseThrow();
    assertThat(rebuiltTrips.contains(sunset)).isTrue();
    assertThat(rebuiltTrips.totalSize()).isEqualTo(2_000L);
  }

  @Test
  void theSnapshotKeepsTheVersionItWasLoadedWith() {
    assertThat(catalog.toSnapshot().version()).isEqualTo(3L);
  }

  @Test
  void aSnapshotWithAnUnknownItemIsRejected() {
    CollectionSnapshot broken =
        new CollectionSnapshot(
            new ItemId(UUID.randomUUID()), "Broken", "", List.of(new ItemId(UUID.randomUUID())));
    CatalogSnapshot snapshot =
        new CatalogSnapshot(
            new CatalogId(UUID.randomUUID()),
            "Library",
            0,
            List.<MediaFileSnapshot>of(),
            List.of(broken));

    assertThatThrownBy(() -> CatalogIndex.fromSnapshot(snapshot))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---------------------------------------------------------------- helpers

  private static CatalogIndex emptyCatalog(long version) {
    return CatalogIndex.fromSnapshot(
        new CatalogSnapshot(
            new CatalogId(UUID.randomUUID()), "Library", version, List.of(), List.of()));
  }

  private MediaFile ingested(String name, char seed) {
    MediaFile file = video(name, "/library/" + name + ".mp4", seed);
    catalog.ingest(file, NOW);
    return file;
  }

  private static ItemId id(CatalogItem item) {
    return item.toSnapshot().id();
  }

  private List<Class<?>> eventTypes() {
    return catalog.pullEvents().stream().<Class<?>>map(Object::getClass).toList();
  }
}
