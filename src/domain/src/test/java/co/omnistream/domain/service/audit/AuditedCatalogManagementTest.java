package co.omnistream.domain.service.audit;

import static co.omnistream.domain.service.audit.AuditSupport.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.model.catalog.DuplicateAction;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.IndexingStatus;
import co.omnistream.domain.model.catalog.IngestionResult;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.service.audit.AuditSupport.FakePort;
import co.omnistream.domain.service.audit.AuditSupport.InMemoryAuditLog;
import co.omnistream.domain.service.audit.AuditSupport.TrackingUnitOfWork;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditedCatalogManagementTest {

  private final TrackingUnitOfWork unitOfWork = new TrackingUnitOfWork();
  private final InMemoryAuditLog auditLog = new InMemoryAuditLog(unitOfWork);
  private final FakePort<ICatalogManagement> service = new FakePort<>(ICatalogManagement.class);
  private final FakePort<ICatalogRepository> catalog = new FakePort<>(ICatalogRepository.class);
  private final ICatalogManagement audited =
      new AuditedCatalogManagement(service.port, catalog.port, auditLog, unitOfWork, CLOCK);

  private final UserId ana = new UserId(UUID.randomUUID());
  private final ItemId file = new ItemId(UUID.randomUUID());

  @Test
  void anUploadedFileIsRecordedWithItsName() {
    service.answer("uploadFile", new IngestionResult(file, false, Optional.empty()));

    audited.uploadFile(ana, "Setlist.pdf", new ByteArrayInputStream(new byte[0]));

    AuditEntry entry = auditLog.only();
    assertThat(entry.action()).isEqualTo(AuditAction.FILE_ADDED);
    assertThat(entry.resource()).contains(file);
    assertThat(entry.detail()).isEqualTo("Setlist.pdf");
  }

  @Test
  void aKeptDuplicateSaysSoAndARejectedOneIsNotRecorded() {
    Path path = Path.of("/media/song.mp3");
    service.answer(
        "ingestFile", new IngestionResult(file, true, Optional.of(DuplicateAction.KEEP_BOTH)));
    audited.ingestFile(ana, path);

    service.answer(
        "ingestFile", new IngestionResult(file, true, Optional.of(DuplicateAction.REJECT)));
    audited.ingestFile(ana, path);

    assertThat(auditLog.only().detail()).isEqualTo(path + " (duplicate: KEEP_BOTH)");
  }

  @Test
  void aDeletedFileKeepsItsNameInTheTrail() {
    catalog.answer("findFile", Optional.of(snapshot("Router manual.pdf")));

    audited.deleteFile(ana, file);

    assertThat(service.calls).containsExactly("deleteFile");
    AuditEntry entry = auditLog.only();
    assertThat(entry.action()).isEqualTo(AuditAction.FILE_DELETED);
    assertThat(entry.resource()).contains(file);
    assertThat(entry.detail()).isEqualTo("Router manual.pdf");
  }

  @Test
  void editingMetadataRecordsTheKeyAndTheValue() {
    audited.editMetadata(ana, file, "title", "Night train");

    assertThat(auditLog.only().detail()).isEqualTo("title = Night train");
  }

  @Test
  void administeringTheLibraryIsRecorded() {
    service.answer("reindexLibrary", 42).answer("backupCatalog", Path.of("/backups/catalog-1.zip"));

    audited.reindexLibrary(ana, Path.of("/media"));
    audited.backupCatalog(ana, Path.of("/backups"));
    audited.configureLibraryPath(ana, Path.of("/srv/media"));

    List<AuditEntry> entries = auditLog.entries;
    assertThat(entries.stream().map(AuditEntry::action).toList())
        .containsExactly(
            AuditAction.LIBRARY_REINDEXED,
            AuditAction.BACKUP_CREATED,
            AuditAction.LIBRARY_PATH_CHANGED);
    assertThat(entries.get(0).detail()).isEqualTo("42 files in /media");
    assertThat(entries.get(1).detail()).isEqualTo("/backups/catalog-1.zip");
    assertThat(entries.get(2).detail()).isEqualTo("/srv/media");
  }

  @Test
  void readingRecordsNothing() {
    service.answer("detectDuplicates", List.of());

    audited.detectDuplicates(ana);

    assertThat(auditLog.entries).isEmpty();
  }

  private MediaFileSnapshot snapshot(String name) {
    return new MediaFileSnapshot(
        file,
        name,
        Path.of("/media/" + name),
        new Fingerprint("SHA-256", "a".repeat(64)),
        MediaType.DOCUMENT,
        1024,
        Duration.ZERO,
        IndexingStatus.INDEXED,
        AuditSupport.NOW,
        List.of(),
        Set.of());
  }
}
