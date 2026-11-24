package co.omnistream.domain.service;

import static co.omnistream.domain.model.access.AccessFixtures.item;
import static co.omnistream.domain.model.access.AccessFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.event.FileClassified;
import co.omnistream.domain.event.FileDeleted;
import co.omnistream.domain.event.FileIngested;
import co.omnistream.domain.event.FileModified;
import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.FileRejected;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.DuplicateAction;
import co.omnistream.domain.model.catalog.IngestionResult;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.model.catalog.MetadataSource;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.model.catalog.TagSource;
import co.omnistream.domain.model.playback.PlaybackSession;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.policy.DeduplicationPolicy;
import co.omnistream.domain.port.dto.ServerStatus;
import co.omnistream.domain.port.out.IMetadataExtractor;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.port.out.memory.InMemoryCatalogRepository;
import co.omnistream.domain.port.out.memory.InMemoryConfigurationRepository;
import co.omnistream.domain.port.out.memory.InMemoryDataBackup;
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
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-21T18:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final InMemoryUserRepository users = new InMemoryUserRepository();
  private final InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
  private final InMemoryCatalogRepository catalogRepository = new InMemoryCatalogRepository(outbox);
  private final InMemorySessionRepository sessionRepository = new InMemorySessionRepository();
  private final InMemoryFileStorage storage = new InMemoryFileStorage(5_000_000L);
  private final InMemoryDataBackup dataBackup = new InMemoryDataBackup();
  private final InMemoryConfigurationRepository configuration =
      new InMemoryConfigurationRepository();
  private final CountingUnitOfWork unitOfWork = new CountingUnitOfWork();
  private final DeduplicationPolicy deduplicationPolicy = new DeduplicationPolicy();

  private CatalogService service;
  private UserId adminId;
  private UserId anaId;
  private UserId luisId;

  @BeforeEach
  void setUp() {
    configuration.saveLibraryPath(Path.of("/library"));
    service = serviceWith(new InMemoryMetadataExtractor());

    User admin = user("root", Role.ADMIN);
    User ana = user("ana", Role.USER);
    User luis = user("luis", Role.USER);
    users.save(admin);
    users.save(ana);
    users.save(luis);
    adminId = admin.toSnapshot().id();
    anaId = ana.toSnapshot().id();
    luisId = luis.toSnapshot().id();
  }

  private CatalogService serviceWith(IMetadataExtractor extractor) {
    IngestionStep extraction = new ExtractionStep(storage, extractor, CLOCK);
    extraction
        .chain(new DuplicatesStep(deduplicationPolicy))
        .chain(new ClassificationStep(List.of(new MediaTypeRule(), new DateRule())))
        .chain(new IndexingStep());
    return new CatalogService(
        users,
        new AccessPolicy(),
        CLOCK,
        extraction,
        catalogRepository,
        sessionRepository,
        storage,
        dataBackup,
        configuration,
        deduplicationPolicy,
        unitOfWork);
  }

  private void put(Path path, byte[] bytes) {
    storage.write(path, new ByteArrayInputStream(bytes));
  }

  @Test
  void ingestFileIndexesClassifiesPersistsOutboxAndGrantsManageToUploader() {
    Path path = Path.of("/library/concert.mp4");
    put(path, "concert-video".getBytes(StandardCharsets.UTF_8));
    CatalogService customService =
        serviceWith(
            new InMemoryMetadataExtractor(
                List.of(
                    new MetadataEntry("captureDate", "2026-03-10", MetadataSource.EXTRACTED),
                    new MetadataEntry("duration", "PT2M", MetadataSource.EXTRACTED)),
                MediaType.values()));
    int executionsBefore = unitOfWork.executions;
    int outboxBefore = outbox.pending().size();

    IngestionResult result = customService.ingestFile(anaId, path);

    assertThat(unitOfWork.executions).isEqualTo(executionsBefore + 1);
    assertThat(result.wasIndexed()).isTrue();
    MediaFileSnapshot file = catalogRepository.findFile(result.fileId()).orElseThrow();
    assertThat(file.tags())
        .containsExactlyInAnyOrder(
            new Tag("video", TagSource.AUTOMATIC),
            new Tag("2026", TagSource.AUTOMATIC),
            new Tag("2026-03", TagSource.AUTOMATIC));
    assertThat(eventTypes(outbox.pending().subList(outboxBefore, outbox.pending().size())))
        .containsExactly(FileIngested.class, FileClassified.class);
    assertThat(users.findById(anaId).orElseThrow().canAccess(result.fileId(), AccessType.DELETE))
        .isTrue();
  }

  @Test
  void ingestFileSkipsSavingOnRejectedDuplicateAndRejectsGuestsOrDeactivatedUsers() {
    Path path = Path.of("/library/song.mp3");
    put(path, "audio-bytes".getBytes(StandardCharsets.UTF_8));
    IngestionResult first = service.ingestFile(anaId, path);
    int outboxBefore = outbox.pending().size();
    long versionAfterFirst = catalogRepository.getCatalog().toSnapshot().version();

    IngestionResult duplicate = service.ingestFile(anaId, path);

    assertThat(duplicate.wasIndexed()).isFalse();
    assertThat(duplicate.action()).contains(DuplicateAction.REJECT);
    assertThat(duplicate.fileId()).isEqualTo(first.fileId());
    assertThat(catalogRepository.getCatalog().toSnapshot().version()).isEqualTo(versionAfterFirst);
    assertThat(outbox.pending()).hasSize(outboxBefore);

    User guest = User.createGuest(item(), PermissionLevel.MANAGE);
    users.save(guest);
    User deactivated = user("old", Role.USER);
    deactivated.deactivate();
    users.save(deactivated);

    assertThatThrownBy(() -> service.ingestFile(guest.toSnapshot().id(), path))
        .isInstanceOf(AccessDenied.class);
    assertThatThrownBy(() -> service.ingestFile(deactivated.toSnapshot().id(), path))
        .isInstanceOf(AccessDenied.class);
  }

  @Test
  void uploadFileWritesUnderLibraryPathGrantsManageAndCleansUpRejectedFiles() {
    byte[] content = "photo-bytes".getBytes(StandardCharsets.UTF_8);

    IngestionResult result =
        service.uploadFile(anaId, "sunset.jpg", new ByteArrayInputStream(content));

    Path expectedTarget = Path.of("/library/sunset.jpg");
    assertThat(storage.exists(expectedTarget)).isTrue();
    assertThat(result.wasIndexed()).isTrue();
    assertThat(users.findById(anaId).orElseThrow().canAccess(result.fileId(), AccessType.DELETE))
        .isTrue();

    assertThatThrownBy(() ->
            service.uploadFile(
                anaId,
                "../escape.mp4",
                new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(FileRejected.class);
    assertThatThrownBy(() ->
            service.uploadFile(
                anaId,
                "sub/video.mp4",
                new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(FileRejected.class);
    assertThatThrownBy(() ->
            service.uploadFile(
                anaId,
                "malware.exe",
                new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(FileRejected.class);
    assertThat(storage.exists(Path.of("/library/malware.exe"))).isFalse();
  }

  @Test
  void everyFileTheTextEditorOpensCanBeUploaded() {
    for (String name : List.of("tone.py", "fix.patch", "changes.diff", "Main.java")) {
      IngestionResult result =
          service.uploadFile(
              anaId, name, new ByteArrayInputStream(name.getBytes(StandardCharsets.UTF_8)));

      assertThat(result.wasIndexed()).isTrue();
      assertThat(catalogRepository.findFile(result.fileId()).orElseThrow().type())
          .isEqualTo(MediaType.DOCUMENT);
    }
  }

  @Test
  void reindexLibraryIndexesSupportedFilesSkipsUnsupportedAndRequiresAdmin() {
    Path root = Path.of("/library");
    put(root.resolve("a.mp4"), "video-1".getBytes(StandardCharsets.UTF_8));
    put(root.resolve("b.png"), "image-2".getBytes(StandardCharsets.UTF_8));
    put(root.resolve("ignored.bin"), "binary".getBytes(StandardCharsets.UTF_8));
    put(Path.of("/other/c.mp3"), "outside".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> service.reindexLibrary(anaId, root)).isInstanceOf(AccessDenied.class);

    int firstPass = service.reindexLibrary(adminId, root);
    int secondPass = service.reindexLibrary(adminId, root);

    assertThat(firstPass).isEqualTo(2);
    assertThat(secondPass).isEqualTo(0);
    assertThat(catalogRepository.getCatalog().totalFiles()).isEqualTo(2);
  }

  @Test
  void detectDuplicatesReturnsOnlyVisibleDuplicateCopies() {
    byte[] sharedContent = "duplicate-video".getBytes(StandardCharsets.UTF_8);
    Path anaPath = Path.of("/library/ana-copy.mp4");
    Path backupPath = Path.of("/backup/ana-copy.mp4");
    Path luisPath = Path.of("/library/luis-unique.mp4");
    put(anaPath, sharedContent);
    put(backupPath, sharedContent);
    put(luisPath, "unique-video".getBytes(StandardCharsets.UTF_8));

    IngestionResult first = service.ingestFile(anaId, anaPath);
    IngestionResult second = service.ingestFile(luisId, backupPath);
    service.ingestFile(luisId, luisPath);

    CatalogIndex catalog = catalogRepository.getCatalog();
    ItemId folderId = catalog.createCollection("Shared", NOW).toSnapshot().id();
    catalog.addToCollection(folderId, second.fileId(), NOW);
    catalogRepository.save(catalog);

    User ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(folderId, PermissionLevel.READ));
    users.save(ana);

    List<MediaFileSnapshot> anaDuplicates = service.detectDuplicates(anaId);
    List<MediaFileSnapshot> luisDuplicates = service.detectDuplicates(luisId);

    assertThat(anaDuplicates)
        .extracting(MediaFileSnapshot::id)
        .containsExactlyInAnyOrder(first.fileId(), second.fileId());
    assertThat(luisDuplicates).extracting(MediaFileSnapshot::id).containsExactly(second.fileId());
  }

  @Test
  void editMetadataAndDeleteFileEnforceAccessAndCollectionInheritance() {
    Path path = Path.of("/library/doc.pdf");
    put(path, "pdf-bytes".getBytes(StandardCharsets.UTF_8));
    IngestionResult ingested = service.ingestFile(luisId, path);
    ItemId fileId = ingested.fileId();

    CatalogIndex catalog = catalogRepository.getCatalog();
    ItemId folderId = catalog.createCollection("Team", NOW).toSnapshot().id();
    catalog.addToCollection(folderId, fileId, NOW);
    catalogRepository.save(catalog);

    assertThatThrownBy(() -> service.editMetadata(anaId, fileId, "title", "Spec"))
        .isInstanceOf(AccessDenied.class);

    User ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(folderId, PermissionLevel.WRITE));
    users.save(ana);

    int outboxBeforeEdit = outbox.pending().size();
    service.editMetadata(anaId, fileId, "title", "Spec");

    MediaFileSnapshot updated = catalogRepository.findFile(fileId).orElseThrow();
    assertThat(updated.metadata())
        .contains(new MetadataEntry("title", "Spec", MetadataSource.MANUAL));
    assertThat(eventTypes(outbox.pending().subList(outboxBeforeEdit, outbox.pending().size())))
        .containsExactly(FileModified.class);

    assertThatThrownBy(() -> service.deleteFile(anaId, fileId)).isInstanceOf(AccessDenied.class);

    int outboxBeforeDelete = outbox.pending().size();
    service.deleteFile(luisId, fileId);

    assertThat(catalogRepository.findFile(fileId)).isEmpty();
    assertThat(outbox.pending().subList(outboxBeforeDelete, outbox.pending().size()))
        .anyMatch(event -> event instanceof FileDeleted);
    assertThatThrownBy(() -> service.deleteFile(adminId, item()))
        .isInstanceOf(ResourceNotFound.class);
  }

  @Test
  void adminOperationsBackUpConfigureLibraryPathAndMonitorStatus() {
    Path path = Path.of("/library/clip.mp4");
    put(path, "clip".getBytes(StandardCharsets.UTF_8));
    IngestionResult ingested = service.ingestFile(adminId, path);

    PlaybackSession active =
        PlaybackSession.start(ingested.fileId(), anaId, Duration.ofMinutes(2), NOW);
    PlaybackSession paused =
        PlaybackSession.start(ingested.fileId(), luisId, Duration.ofMinutes(2), NOW);
    paused.pause();
    PlaybackSession finished =
        PlaybackSession.start(ingested.fileId(), adminId, Duration.ofMinutes(2), NOW);
    finished.finish();
    sessionRepository.save(active);
    sessionRepository.save(paused);
    sessionRepository.save(finished);

    assertThatThrownBy(() -> service.backupCatalog(anaId, Path.of("/backups")))
        .isInstanceOf(AccessDenied.class);
    assertThatThrownBy(() -> service.configureLibraryPath(anaId, Path.of("/media")))
        .isInstanceOf(AccessDenied.class);
    assertThatThrownBy(() -> service.monitorServerStatus(anaId)).isInstanceOf(AccessDenied.class);

    Path backup = service.backupCatalog(adminId, Path.of("/backups"));
    service.configureLibraryPath(adminId, Path.of("/media"));
    ServerStatus status = service.monitorServerStatus(adminId);

    assertThat(backup).isEqualTo(Path.of("/backups"));
    assertThat(dataBackup.backups()).containsExactly(Path.of("/backups"));
    assertThat(configuration.getLibraryPath()).isEqualTo(Path.of("/media"));
    assertThat(status).isEqualTo(new ServerStatus(1, 5_000_000L, 2));
  }

  private static List<Class<?>> eventTypes(List<DomainEvent> events) {
    return events.stream().<Class<?>>map(Object::getClass).toList();
  }

  private static final class CountingUnitOfWork implements IUnitOfWork {
    private final InMemoryUnitOfWork delegate = new InMemoryUnitOfWork();
    int executions;

    @Override
    public <T> T execute(Supplier<T> work) {
      executions++;
      return delegate.execute(work);
    }
  }
}
