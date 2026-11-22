package co.omnistream.domain.service;

import static co.omnistream.domain.model.access.AccessFixtures.item;
import static co.omnistream.domain.model.access.AccessFixtures.user;
import static co.omnistream.domain.model.catalog.CatalogFixtures.fingerprint;
import static co.omnistream.domain.model.catalog.CatalogFixtures.video;
import static co.omnistream.domain.service.ServiceTestDoubles.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.event.CatalogEvent;
import co.omnistream.domain.event.CollectionCreated;
import co.omnistream.domain.event.CollectionModified;
import co.omnistream.domain.event.FileModified;
import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.CyclicCollection;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.Collection;
import co.omnistream.domain.model.catalog.MediaFile;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.model.catalog.MetadataSource;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.model.catalog.TagSource;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.dto.CollectionCommand;
import co.omnistream.domain.port.dto.SearchCriteria;
import co.omnistream.domain.service.ServiceTestDoubles.InMemoryCatalog;
import co.omnistream.domain.service.ServiceTestDoubles.InMemoryUsers;
import co.omnistream.domain.service.ServiceTestDoubles.TrackingUnitOfWork;
import co.omnistream.domain.snapshot.CatalogItemSnapshot;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchServiceTest {

  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final InMemoryUsers users = new InMemoryUsers();
  private final InMemoryCatalog catalogRepository = new InMemoryCatalog();
  private final TrackingUnitOfWork unitOfWork = new TrackingUnitOfWork();

  private SearchService service;
  private UserId adminId;
  private UserId anaId;
  private UserId luisId;
  private UserId guestId;

  @BeforeEach
  void setUp() {
    service = new SearchService(users, new AccessPolicy(), CLOCK, catalogRepository, unitOfWork);

    User admin = user("admin", Role.ADMIN);
    User ana = user("ana", Role.USER);
    User luis = user("luis", Role.USER);
    User guest = User.createGuest(item(), PermissionLevel.MANAGE);
    users.save(admin);
    users.save(ana);
    users.save(luis);
    users.save(guest);

    adminId = admin.toSnapshot().id();
    anaId = ana.toSnapshot().id();
    luisId = luis.toSnapshot().id();
    guestId = guest.toSnapshot().id();
  }

  @Test
  void searchContentFindsVisibleFilesAndCollectionsAndIgnoresHiddenOrBlank() {
    CatalogIndex catalog = catalogRepository.getCatalog();
    MediaFile beachVideo = video("Beach Sunset", "/library/beach.mp4", 'a');
    MediaFile jazzTrack =
        MediaFile.create(
            "Night Jazz",
            Path.of("/library/jazz.mp3"),
            fingerprint('b'),
            MediaType.AUDIO,
            2_000L,
            Duration.ofMinutes(4),
            NOW);
    jazzTrack.addMetadata(new MetadataEntry("artist", "Miles Trio", MetadataSource.EXTRACTED));
    jazzTrack.tag(new Tag("Música", TagSource.MANUAL));
    MediaFile secretClip = video("Beach Secret", "/library/secret.mp4", 'c');
    catalog.ingest(beachVideo, NOW);
    catalog.ingest(jazzTrack, NOW);
    catalog.ingest(secretClip, NOW);

    Collection holidays = catalog.createCollection("Beach Holidays", NOW);
    catalog.addToCollection(holidays.toSnapshot().id(), beachVideo.toSnapshot().id(), NOW);
    CatalogSnapshot baseSnapshot = catalog.toSnapshot();
    CollectionSnapshot describedHolidays =
        new CollectionSnapshot(
            holidays.toSnapshot().id(),
            "Beach Holidays",
            "Summer memories",
            holidays.toSnapshot().items());
    CatalogIndex describedCatalog =
        CatalogIndex.fromSnapshot(
            new CatalogSnapshot(
                baseSnapshot.id(),
                baseSnapshot.name(),
                baseSnapshot.version(),
                baseSnapshot.files(),
                List.of(describedHolidays)));
    catalogRepository.save(describedCatalog);

    User ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(holidays.toSnapshot().id(), PermissionLevel.READ));
    ana.grant(new Permission(jazzTrack.toSnapshot().id(), PermissionLevel.READ));
    users.save(ana);

    List<CatalogItemSnapshot> beachMatches = service.searchContent(anaId, "beach");
    List<CatalogItemSnapshot> musicMatches = service.searchContent(anaId, "musica");
    List<CatalogItemSnapshot> artistMatches = service.searchContent(anaId, "miles");
    List<CatalogItemSnapshot> descriptionMatches = service.searchContent(anaId, "memories");

    assertThat(beachMatches)
        .extracting(CatalogItemSnapshot::id)
        .containsExactly(holidays.toSnapshot().id(), beachVideo.toSnapshot().id());
    assertThat(musicMatches)
        .extracting(CatalogItemSnapshot::id)
        .containsExactly(jazzTrack.toSnapshot().id());
    assertThat(artistMatches)
        .extracting(CatalogItemSnapshot::id)
        .containsExactly(jazzTrack.toSnapshot().id());
    assertThat(descriptionMatches)
        .extracting(CatalogItemSnapshot::id)
        .containsExactly(holidays.toSnapshot().id());
    assertThat(service.searchContent(anaId, "   ")).isEmpty();

    User inactive = users.findById(luisId).orElseThrow();
    inactive.deactivate();
    users.save(inactive);
    assertThatThrownBy(() -> service.searchContent(luisId, "beach"))
        .isInstanceOf(AccessDenied.class);
  }

  @Test
  void filterByCriteriaAppliesTypeTagsAndTextEnforcesAccessAndSortsDeterministically() {
    CatalogIndex catalog = catalogRepository.getCatalog();
    Instant earlier = NOW.minusSeconds(60);
    MediaFile zetaVideo =
        MediaFile.create(
            "Zeta Clip",
            Path.of("/library/zeta.mp4"),
            fingerprint('a'),
            MediaType.VIDEO,
            1_000L,
            Duration.ofMinutes(1),
            NOW);
    MediaFile alphaLater =
        MediaFile.create(
            "alpha clip",
            Path.of("/library/alpha2.mp4"),
            fingerprint('b'),
            MediaType.VIDEO,
            1_000L,
            Duration.ofMinutes(1),
            NOW);
    MediaFile alphaEarlier =
        MediaFile.create(
            "Alpha Clip",
            Path.of("/library/alpha1.mp4"),
            fingerprint('c'),
            MediaType.VIDEO,
            1_000L,
            Duration.ofMinutes(1),
            earlier);
    MediaFile audioSameTag =
        MediaFile.create(
            "Alpha Audio",
            Path.of("/library/alpha.mp3"),
            fingerprint('d'),
            MediaType.AUDIO,
            1_000L,
            Duration.ofMinutes(1),
            NOW);
    MediaFile hiddenVideo = video("Alpha Hidden", "/library/hidden.mp4", 'e');

    for (MediaFile file : List.of(zetaVideo, alphaLater, alphaEarlier, audioSameTag, hiddenVideo)) {
      file.tag(new Tag("Travel", TagSource.MANUAL));
      catalog.ingest(file, NOW);
    }
    catalogRepository.save(catalog);

    User ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(zetaVideo.toSnapshot().id(), PermissionLevel.READ));
    ana.grant(new Permission(alphaLater.toSnapshot().id(), PermissionLevel.READ));
    ana.grant(new Permission(alphaEarlier.toSnapshot().id(), PermissionLevel.READ));
    ana.grant(new Permission(audioSameTag.toSnapshot().id(), PermissionLevel.READ));
    users.save(ana);

    SearchCriteria criteria =
        new SearchCriteria(
            Optional.of("clip"),
            Optional.of(MediaType.VIDEO),
            Set.of(new Tag("travel", TagSource.AUTOMATIC)));

    List<MediaFileSnapshot> filtered = service.filterByCriteria(anaId, criteria);
    List<MediaFileSnapshot> unfiltered =
        service.filterByCriteria(
            anaId, new SearchCriteria(Optional.empty(), Optional.empty(), Set.of()));

    assertThat(filtered)
        .extracting(MediaFileSnapshot::id)
        .containsExactly(
            alphaEarlier.toSnapshot().id(),
            alphaLater.toSnapshot().id(),
            zetaVideo.toSnapshot().id());
    assertThat(unfiltered)
        .extracting(MediaFileSnapshot::id)
        .containsExactly(
            audioSameTag.toSnapshot().id(),
            alphaEarlier.toSnapshot().id(),
            alphaLater.toSnapshot().id(),
            zetaVideo.toSnapshot().id());
    assertThatThrownBy(() -> service.filterByCriteria(anaId, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void browseLibraryRootAndCollectionRespectNestingAndAccessControl() {
    CatalogIndex catalog = catalogRepository.getCatalog();
    MediaFile looseVisible = video("Loose Visible", "/library/loose-v.mp4", 'a');
    MediaFile looseHidden = video("Loose Hidden", "/library/loose-h.mp4", 'b');
    MediaFile nestedFile = video("Nested File", "/library/nested.mp4", 'c');
    catalog.ingest(looseVisible, NOW);
    catalog.ingest(looseHidden, NOW);
    catalog.ingest(nestedFile, NOW);

    Collection rootFolder = catalog.createCollection("Trips", NOW);
    Collection subFolder = catalog.createCollection("Beach", NOW);
    Collection hiddenFolder = catalog.createCollection("Private", NOW);
    catalog.addToCollection(subFolder.toSnapshot().id(), nestedFile.toSnapshot().id(), NOW);
    catalog.addToCollection(rootFolder.toSnapshot().id(), subFolder.toSnapshot().id(), NOW);
    catalog.addToCollection(hiddenFolder.toSnapshot().id(), looseHidden.toSnapshot().id(), NOW);
    catalogRepository.save(catalog);

    User ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(rootFolder.toSnapshot().id(), PermissionLevel.READ));
    ana.grant(new Permission(looseVisible.toSnapshot().id(), PermissionLevel.READ));
    users.save(ana);

    List<CatalogItemSnapshot> anaRoot = service.browseLibrary(anaId);
    List<CatalogItemSnapshot> rootContents =
        service.browseLibrary(anaId, rootFolder.toSnapshot().id());
    List<CatalogItemSnapshot> subContents =
        service.browseLibrary(anaId, subFolder.toSnapshot().id());

    assertThat(anaRoot)
        .extracting(CatalogItemSnapshot::id)
        .containsExactly(rootFolder.toSnapshot().id(), looseVisible.toSnapshot().id());
    assertThat(rootContents)
        .extracting(CatalogItemSnapshot::id)
        .containsExactly(subFolder.toSnapshot().id());
    assertThat(subContents)
        .extracting(CatalogItemSnapshot::id)
        .containsExactly(nestedFile.toSnapshot().id());

    assertThatThrownBy(() -> service.browseLibrary(anaId, hiddenFolder.toSnapshot().id()))
        .isInstanceOf(AccessDenied.class);
    assertThatThrownBy(() -> service.browseLibrary(anaId, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.browseLibrary(adminId, item()))
        .isInstanceOf(ResourceNotFound.class);
    assertThatThrownBy(() -> service.browseLibrary(adminId, looseVisible.toSnapshot().id()))
        .isInstanceOf(ResourceNotFound.class);
  }

  @Test
  void manageCollectionsCreatesCollectionGrantsManageAndValidatesItemsAndActor() {
    CatalogIndex catalog = catalogRepository.getCatalog();
    MediaFile anaFile = video("Ana Clip", "/library/ana.mp4", 'a');
    MediaFile luisFile = video("Luis Clip", "/library/luis.mp4", 'b');
    catalog.ingest(anaFile, NOW);
    catalog.ingest(luisFile, NOW);
    catalogRepository.save(catalog);

    User ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(anaFile.toSnapshot().id(), PermissionLevel.READ));
    users.save(ana);
    catalogRepository.outbox.clear();

    CollectionSnapshot created =
        service.manageCollections(
            anaId,
            new CollectionCommand(
                Optional.empty(), Optional.of("Favorites"), List.of(anaFile.toSnapshot().id())));

    assertThat(created.name()).isEqualTo("Favorites");
    assertThat(created.items()).containsExactly(anaFile.toSnapshot().id());
    assertThat(unitOfWork.executions).isEqualTo(1);
    assertThat(eventTypes(catalogRepository.outbox))
        .containsExactly(CollectionCreated.class, CollectionModified.class);

    User updatedAna = users.findById(anaId).orElseThrow();
    assertThat(updatedAna.toSnapshot().permissions())
        .contains(new Permission(created.id(), PermissionLevel.MANAGE));
    assertThat(updatedAna.canAccess(created.id(), AccessType.DELETE)).isTrue();

    assertThat(service.browseLibrary(anaId, created.id()))
        .extracting(CatalogItemSnapshot::id)
        .containsExactly(anaFile.toSnapshot().id());

    assertThatThrownBy(() ->
            service.manageCollections(
                anaId,
                new CollectionCommand(
                    Optional.empty(),
                    Optional.of("Unauthorized"),
                    List.of(luisFile.toSnapshot().id()))))
        .isInstanceOf(AccessDenied.class);

    assertThatThrownBy(() ->
            service.manageCollections(
                guestId,
                new CollectionCommand(Optional.empty(), Optional.of("GuestList"), List.of())))
        .isInstanceOf(AccessDenied.class);
  }

  @Test
  void manageCollectionsUpdatesExistingRenamesAddsItemsAndDetectsCyclesAndMissingTargets() {
    CatalogIndex catalog = catalogRepository.getCatalog();
    MediaFile file = video("Clip", "/library/clip.mp4", 'a');
    catalog.ingest(file, NOW);
    Collection parent = catalog.createCollection("Parent", NOW);
    Collection child = catalog.createCollection("Child", NOW);
    catalog.addToCollection(parent.toSnapshot().id(), child.toSnapshot().id(), NOW);
    catalogRepository.save(catalog);

    User ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(parent.toSnapshot().id(), PermissionLevel.READ));
    ana.grant(new Permission(child.toSnapshot().id(), PermissionLevel.WRITE));
    ana.grant(new Permission(file.toSnapshot().id(), PermissionLevel.READ));
    users.save(ana);
    catalogRepository.outbox.clear();

    CollectionSnapshot updated =
        service.manageCollections(
            anaId,
            new CollectionCommand(
                Optional.of(child.toSnapshot().id()),
                Optional.of("Summer Beach"),
                List.of(file.toSnapshot().id())));

    assertThat(updated.name()).isEqualTo("Summer Beach");
    assertThat(updated.items()).containsExactly(file.toSnapshot().id());
    assertThat(eventTypes(catalogRepository.outbox)).containsExactly(CollectionModified.class);

    assertThatThrownBy(() ->
            service.manageCollections(
                anaId,
                new CollectionCommand(
                    Optional.of(parent.toSnapshot().id()),
                    Optional.of("Denied Rename"),
                    List.of())))
        .isInstanceOf(AccessDenied.class);

    assertThatThrownBy(() ->
            service.manageCollections(
                anaId,
                new CollectionCommand(
                    Optional.of(child.toSnapshot().id()),
                    Optional.empty(),
                    List.of(parent.toSnapshot().id()))))
        .isInstanceOf(CyclicCollection.class);

    assertThatThrownBy(() ->
            service.manageCollections(
                adminId,
                new CollectionCommand(Optional.of(item()), Optional.of("Missing"), List.of())))
        .isInstanceOf(ResourceNotFound.class);

    assertThatThrownBy(() ->
            service.manageCollections(
                adminId,
                new CollectionCommand(
                    Optional.of(file.toSnapshot().id()), Optional.of("NotCol"), List.of())))
        .isInstanceOf(ResourceNotFound.class);

    assertThatThrownBy(() ->
            service.manageCollections(
                adminId,
                new CollectionCommand(
                    Optional.of(child.toSnapshot().id()), Optional.empty(), List.of(item()))))
        .isInstanceOf(ResourceNotFound.class);
  }

  @Test
  void tagFileAddsManualTagUsingInheritanceRunsInUnitOfWorkAndRejectsUnauthorized() {
    CatalogIndex catalog = catalogRepository.getCatalog();
    MediaFile file = video("Doc", "/library/doc.pdf", 'a');
    catalog.ingest(file, NOW);
    Collection folder = catalog.createCollection("Team", NOW);
    catalog.addToCollection(folder.toSnapshot().id(), file.toSnapshot().id(), NOW);
    catalogRepository.save(catalog);

    User ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(folder.toSnapshot().id(), PermissionLevel.READ));
    users.save(ana);

    assertThatThrownBy(() ->
            service.tagFile(anaId, file.toSnapshot().id(), new Tag("important", TagSource.MANUAL)))
        .isInstanceOf(AccessDenied.class);
    assertThatThrownBy(() ->
            service.tagFile(
                guestId, file.toSnapshot().id(), new Tag("important", TagSource.MANUAL)))
        .isInstanceOf(AccessDenied.class);

    User luis = users.findById(luisId).orElseThrow();
    luis.grant(new Permission(folder.toSnapshot().id(), PermissionLevel.WRITE));
    users.save(luis);
    catalogRepository.outbox.clear();
    int executionsBefore = unitOfWork.executions;

    service.tagFile(luisId, file.toSnapshot().id(), new Tag("Important", TagSource.MANUAL));

    MediaFileSnapshot tagged = catalogRepository.findFile(file.toSnapshot().id()).orElseThrow();
    assertThat(tagged.tags()).containsExactly(new Tag("Important", TagSource.MANUAL));
    assertThat(unitOfWork.executions).isEqualTo(executionsBefore + 1);
    assertThat(eventTypes(catalogRepository.outbox)).containsExactly(FileModified.class);

    assertThatThrownBy(() -> service.tagFile(adminId, item(), new Tag("x", TagSource.MANUAL)))
        .isInstanceOf(ResourceNotFound.class);
    assertThatThrownBy(() ->
            service.tagFile(adminId, folder.toSnapshot().id(), new Tag("x", TagSource.MANUAL)))
        .isInstanceOf(ResourceNotFound.class);
  }

  private static List<Class<?>> eventTypes(List<CatalogEvent> events) {
    return events.stream().<Class<?>>map(Object::getClass).toList();
  }
}
