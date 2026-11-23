package co.omnistream.domain.service;

import static co.omnistream.domain.model.access.AccessFixtures.item;
import static co.omnistream.domain.model.access.AccessFixtures.user;
import static co.omnistream.domain.model.catalog.CatalogFixtures.video;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.event.FileDeleted;
import co.omnistream.domain.event.FileIngested;
import co.omnistream.domain.event.FileModified;
import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.Collection;
import co.omnistream.domain.model.catalog.IngestionResult;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaFile;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.model.catalog.MetadataSource;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.policy.DeduplicationPolicy;
import co.omnistream.domain.port.dto.OfflinePackage;
import co.omnistream.domain.port.dto.SyncDelta;
import co.omnistream.domain.port.out.memory.InMemoryCatalogRepository;
import co.omnistream.domain.port.out.memory.InMemoryChangeLog;
import co.omnistream.domain.port.out.memory.InMemoryConfigurationRepository;
import co.omnistream.domain.port.out.memory.InMemoryDataBackup;
import co.omnistream.domain.port.out.memory.InMemoryEventBus;
import co.omnistream.domain.port.out.memory.InMemoryFileStorage;
import co.omnistream.domain.port.out.memory.InMemoryMetadataExtractor;
import co.omnistream.domain.port.out.memory.InMemoryOutboxRepository;
import co.omnistream.domain.port.out.memory.InMemorySessionRepository;
import co.omnistream.domain.port.out.memory.InMemoryUnitOfWork;
import co.omnistream.domain.port.out.memory.InMemoryUserRepository;
import co.omnistream.domain.service.classification.DateRule;
import co.omnistream.domain.service.classification.MediaTypeRule;
import co.omnistream.domain.service.ingestion.ClassificationStep;
import co.omnistream.domain.service.ingestion.DuplicatesStep;
import co.omnistream.domain.service.ingestion.ExtractionStep;
import co.omnistream.domain.service.ingestion.IndexingStep;
import co.omnistream.domain.service.ingestion.IngestionStep;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SyncServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-21T18:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final InMemoryUserRepository users = new InMemoryUserRepository();
  private final InMemoryChangeLog changeLog = new InMemoryChangeLog();
  private final InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
  private final InMemoryCatalogRepository catalogRepository = new InMemoryCatalogRepository(outbox);
  private final InMemoryFileStorage storage = new InMemoryFileStorage();

  private SyncService service;
  private UserId adminId;
  private UserId anaId;
  private UserId luisId;

  @BeforeEach
  void setUp() {
    service =
        new SyncService(users, new AccessPolicy(), CLOCK, changeLog, catalogRepository, storage);

    User admin = user("admin", Role.ADMIN);
    User ana = user("ana", Role.USER);
    User luis = user("luis", Role.USER);
    users.save(admin);
    users.save(ana);
    users.save(luis);

    adminId = admin.toSnapshot().id();
    anaId = ana.toSnapshot().id();
    luisId = luis.toSnapshot().id();
  }

  private void put(Path path, byte[] bytes) {
    storage.write(path, new ByteArrayInputStream(bytes));
  }

  @Test
  void syncChangesFiltersAddedAndModifiedByViewAccessPassesRemovedThroughAndHonorsSince() {
    CatalogIndex catalog = catalogRepository.getCatalog();
    MediaFile visibleAdded = video("VisibleAdded", "/library/v1.mp4", 'a');
    MediaFile inheritedModified = video("InheritedModified", "/library/v2.mp4", 'b');
    MediaFile hiddenAdded = video("HiddenAdded", "/library/h1.mp4", 'c');
    MediaFile hiddenModified = video("HiddenModified", "/library/h2.mp4", 'd');
    MediaFile olderVisible = video("Older", "/library/old.mp4", 'e');
    catalog.ingest(visibleAdded, NOW);
    catalog.ingest(inheritedModified, NOW);
    catalog.ingest(hiddenAdded, NOW);
    catalog.ingest(hiddenModified, NOW);
    catalog.ingest(olderVisible, NOW);

    Collection folder = catalog.createCollection("Shared", NOW);
    catalog.addToCollection(folder.toSnapshot().id(), inheritedModified.toSnapshot().id(), NOW);
    catalogRepository.save(catalog);

    User ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(visibleAdded.toSnapshot().id(), PermissionLevel.READ));
    ana.grant(new Permission(olderVisible.toSnapshot().id(), PermissionLevel.READ));
    ana.grant(new Permission(folder.toSnapshot().id(), PermissionLevel.READ));
    users.save(ana);

    Instant checkpoint = NOW;
    ItemId removedId = item();
    changeLog.register(new FileIngested(olderVisible.toSnapshot().id(), NOW.minusSeconds(10)));
    changeLog.register(new FileIngested(visibleAdded.toSnapshot().id(), checkpoint.plusSeconds(1)));
    changeLog.register(new FileIngested(hiddenAdded.toSnapshot().id(), checkpoint.plusSeconds(2)));
    changeLog.register(
        new FileModified(inheritedModified.toSnapshot().id(), checkpoint.plusSeconds(3)));
    changeLog.register(
        new FileModified(hiddenModified.toSnapshot().id(), checkpoint.plusSeconds(4)));
    changeLog.register(new FileDeleted(removedId, checkpoint.plusSeconds(5)));

    SyncDelta anaDelta = service.syncChanges(anaId, checkpoint);
    SyncDelta adminDelta = service.syncChanges(adminId, checkpoint);

    assertThat(anaDelta.added()).containsExactly(visibleAdded.toSnapshot().id());
    assertThat(anaDelta.modified()).containsExactly(inheritedModified.toSnapshot().id());
    assertThat(anaDelta.removed()).containsExactly(removedId);

    assertThat(adminDelta.added())
        .containsExactly(visibleAdded.toSnapshot().id(), hiddenAdded.toSnapshot().id());
    assertThat(adminDelta.modified())
        .containsExactly(inheritedModified.toSnapshot().id(), hiddenModified.toSnapshot().id());
    assertThat(adminDelta.removed()).containsExactly(removedId);
  }

  @Test
  void syncChangesRejectsInactiveOrUnknownUserAndNullSince() {
    User luis = users.findById(luisId).orElseThrow();
    luis.deactivate();
    users.save(luis);

    assertThatThrownBy(() -> service.syncChanges(luisId, NOW)).isInstanceOf(AccessDenied.class);
    assertThatThrownBy(() -> service.syncChanges(new UserId(UUID.randomUUID()), NOW))
        .isInstanceOf(ResourceNotFound.class);
    assertThatThrownBy(() -> service.syncChanges(anaId, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void cacheContentReturnsOfflinePackageForAuthorizedUserOrGuestAndRejectsInvalidRequests() {
    CatalogIndex catalog = catalogRepository.getCatalog();
    MediaFile file = video("Song", "/library/song.mp4", 'a');
    file.addMetadata(new MetadataEntry("title", "Sunset", MetadataSource.MANUAL));
    catalog.ingest(file, NOW);
    Collection folder = catalog.createCollection("Downloads", NOW);
    catalog.addToCollection(folder.toSnapshot().id(), file.toSnapshot().id(), NOW);
    catalogRepository.save(catalog);
    put(Path.of("/library/song.mp4"), "offline-data".getBytes(StandardCharsets.UTF_8));

    User ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(folder.toSnapshot().id(), PermissionLevel.READ));
    users.save(ana);

    User guest = User.createGuest(folder.toSnapshot().id(), PermissionLevel.READ);
    users.save(guest);

    OfflinePackage anaPackage = service.cacheContent(anaId, file.toSnapshot().id());
    OfflinePackage guestPackage =
        service.cacheContent(guest.toSnapshot().id(), file.toSnapshot().id());

    assertThat(anaPackage.fileId()).isEqualTo(file.toSnapshot().id());
    assertThat(anaPackage.content()).isEqualTo(Path.of("/library/song.mp4"));
    assertThat(anaPackage.metadata())
        .containsExactly(new MetadataEntry("title", "Sunset", MetadataSource.MANUAL));
    assertThat(guestPackage).isEqualTo(anaPackage);

    assertThatThrownBy(() -> service.cacheContent(luisId, file.toSnapshot().id()))
        .isInstanceOf(AccessDenied.class);
    assertThatThrownBy(() -> service.cacheContent(adminId, item()))
        .isInstanceOf(ResourceNotFound.class);
    assertThatThrownBy(() -> service.cacheContent(adminId, folder.toSnapshot().id()))
        .isInstanceOf(ResourceNotFound.class);

    storage.delete(Path.of("/library/song.mp4"));
    assertThatThrownBy(() -> service.cacheContent(anaId, file.toSnapshot().id()))
        .isInstanceOf(ResourceNotFound.class);
  }

  @Test
  void hasPendingChangesReturnsFalseWhenNoChangesExistAndValidatesInputs() {
    assertThat(service.hasPendingChanges(anaId, NOW)).isFalse();

    changeLog.register(new FileIngested(item(), NOW));
    assertThat(service.hasPendingChanges(anaId, NOW)).isFalse();

    User luis = users.findById(luisId).orElseThrow();
    luis.deactivate();
    users.save(luis);

    assertThatThrownBy(() -> service.hasPendingChanges(luisId, NOW))
        .isInstanceOf(AccessDenied.class);
    assertThatThrownBy(() -> service.hasPendingChanges(anaId, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void hasPendingChangesIgnoresChangesHiddenFromUserAndDetectsVisibleOrRemovedChanges() {
    CatalogIndex catalog = catalogRepository.getCatalog();
    MediaFile hidden = video("Hidden", "/library/hidden.mp4", 'a');
    MediaFile visible = video("Visible", "/library/visible.mp4", 'b');
    catalog.ingest(hidden, NOW);
    catalog.ingest(visible, NOW);
    catalogRepository.save(catalog);

    User ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(visible.toSnapshot().id(), PermissionLevel.READ));
    users.save(ana);

    changeLog.register(new FileIngested(hidden.toSnapshot().id(), NOW.plusSeconds(1)));
    assertThat(service.hasPendingChanges(anaId, NOW)).isFalse();
    assertThat(service.hasPendingChanges(adminId, NOW)).isTrue();

    changeLog.register(new FileModified(visible.toSnapshot().id(), NOW.plusSeconds(2)));
    assertThat(service.hasPendingChanges(anaId, NOW)).isTrue();

    Instant afterVisible = NOW.plusSeconds(3);
    assertThat(service.hasPendingChanges(anaId, afterVisible)).isFalse();

    changeLog.register(new FileDeleted(item(), afterVisible.plusSeconds(1)));
    assertThat(service.hasPendingChanges(anaId, afterVisible)).isTrue();
  }

  @Test
  void endToEndPipelineDispatchesOutboxToSyncObserverAndDeliversFilteredSyncDelta() {
    Clock ingestionClock = Clock.fixed(NOW.plusSeconds(5), ZoneOffset.UTC);
    DeduplicationPolicy deduplicationPolicy = new DeduplicationPolicy();
    IngestionStep ingestionChain =
        new ExtractionStep(storage, new InMemoryMetadataExtractor(), ingestionClock);
    ingestionChain
        .chain(new DuplicatesStep(deduplicationPolicy))
        .chain(new ClassificationStep(List.of(new MediaTypeRule(), new DateRule())))
        .chain(new IndexingStep());

    CatalogService catalogService =
        new CatalogService(
            users,
            new AccessPolicy(),
            ingestionClock,
            ingestionChain,
            catalogRepository,
            new InMemorySessionRepository(),
            storage,
            new InMemoryDataBackup(),
            new InMemoryConfigurationRepository(),
            deduplicationPolicy,
            new InMemoryUnitOfWork());

    InMemoryEventBus eventBus = new InMemoryEventBus();
    eventBus.subscribe(new SyncObserver(changeLog));
    EventDispatchService dispatcher = new EventDispatchService(outbox, eventBus);

    Path anaPath = Path.of("/library/shared.mp4");
    Path luisPath = Path.of("/library/private.mp4");
    put(anaPath, "shared-video".getBytes(StandardCharsets.UTF_8));
    put(luisPath, "private-video".getBytes(StandardCharsets.UTF_8));

    IngestionResult anaFile = catalogService.ingestFile(anaId, anaPath);
    IngestionResult luisFile = catalogService.ingestFile(luisId, luisPath);

    int dispatched = dispatcher.publishPending(10);
    SyncDelta anaDelta = service.syncChanges(anaId, NOW);

    assertThat(dispatched).isEqualTo(4);
    assertThat(outbox.pending()).isEmpty();
    assertThat(service.hasPendingChanges(anaId, NOW)).isTrue();
    assertThat(anaDelta.added()).containsExactly(anaFile.fileId());
    assertThat(anaDelta.modified()).containsExactly(anaFile.fileId());
    assertThat(anaDelta.removed()).isEmpty();
    assertThat(anaDelta.added()).doesNotContain(luisFile.fileId());
  }
}
