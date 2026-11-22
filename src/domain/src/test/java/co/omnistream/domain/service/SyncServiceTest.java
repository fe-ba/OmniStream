package co.omnistream.domain.service;

import static co.omnistream.domain.model.access.AccessFixtures.item;
import static co.omnistream.domain.model.access.AccessFixtures.user;
import static co.omnistream.domain.model.catalog.CatalogFixtures.video;
import static co.omnistream.domain.service.ServiceTestDoubles.NOW;
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
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaFile;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.model.catalog.MetadataSource;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.dto.OfflinePackage;
import co.omnistream.domain.port.dto.SyncDelta;
import co.omnistream.domain.service.ServiceTestDoubles.InMemoryCatalog;
import co.omnistream.domain.service.ServiceTestDoubles.InMemoryChangeLog;
import co.omnistream.domain.service.ServiceTestDoubles.InMemoryFileStorage;
import co.omnistream.domain.service.ServiceTestDoubles.InMemoryOutbox;
import co.omnistream.domain.service.ServiceTestDoubles.InMemoryUsers;
import co.omnistream.domain.service.ServiceTestDoubles.RecordingEventPublisher;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class SyncServiceTest {

  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final InMemoryUsers users = new InMemoryUsers();
  private final InMemoryChangeLog changeLog = new InMemoryChangeLog();
  private final InMemoryCatalog catalogRepository = new InMemoryCatalog();
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
    storage.put(Path.of("/library/song.mp4"), "offline-data".getBytes(StandardCharsets.UTF_8));

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
  @Disabled("Waiting for #5: SyncDelta.isEmpty()")
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
  }

  @Test
  void endToEndPipelineDispatchesOutboxToSyncObserverAndDeliversFilteredSyncDelta() {
    InMemoryOutbox outbox = new InMemoryOutbox();
    RecordingEventPublisher publisher = new RecordingEventPublisher();
    publisher.observers.add(new SyncObserver(changeLog));
    EventDispatchService dispatcher = new EventDispatchService(outbox, publisher);

    Instant eventTime = NOW.plusSeconds(5);
    CatalogIndex catalog = catalogRepository.getCatalog();
    MediaFile shared = video("Shared", "/library/shared.mp4", 'a');
    MediaFile privateFile = video("Private", "/library/private.mp4", 'b');
    catalog.ingest(shared, eventTime);
    catalog.ingest(privateFile, eventTime);
    catalogRepository.save(catalog);

    User ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(shared.toSnapshot().id(), PermissionLevel.READ));
    users.save(ana);

    outbox.enqueueAll(catalogRepository.outbox);
    int dispatched = dispatcher.publishPending(10);
    SyncDelta delta = service.syncChanges(anaId, NOW);

    assertThat(dispatched).isEqualTo(2);
    assertThat(outbox.pendingCount()).isZero();
    assertThat(delta.added()).containsExactly(shared.toSnapshot().id());
    assertThat(delta.modified()).isEmpty();
    assertThat(delta.removed()).isEmpty();
  }
}
