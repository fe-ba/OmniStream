package co.omnistream.domain.service.audit;

import static co.omnistream.domain.service.audit.AuditSupport.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.model.catalog.TagSource;
import co.omnistream.domain.port.dto.CollectionCommand;
import co.omnistream.domain.port.dto.OfflinePackage;
import co.omnistream.domain.port.in.ISearch;
import co.omnistream.domain.port.in.ISync;
import co.omnistream.domain.service.audit.AuditSupport.FakePort;
import co.omnistream.domain.service.audit.AuditSupport.InMemoryAuditLog;
import co.omnistream.domain.service.audit.AuditSupport.TrackingUnitOfWork;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditedSearchAndSyncTest {

  private final TrackingUnitOfWork unitOfWork = new TrackingUnitOfWork();
  private final InMemoryAuditLog auditLog = new InMemoryAuditLog(unitOfWork);
  private final FakePort<ISearch> search = new FakePort<>(ISearch.class);
  private final FakePort<ISync> sync = new FakePort<>(ISync.class);
  private final ISearch auditedSearch = new AuditedSearch(search.port, auditLog, unitOfWork, CLOCK);
  private final ISync auditedSync = new AuditedSync(sync.port, auditLog, unitOfWork, CLOCK);

  private final UserId ana = new UserId(UUID.randomUUID());
  private final ItemId collection = new ItemId(UUID.randomUUID());
  private final ItemId file = new ItemId(UUID.randomUUID());

  @Test
  void aCommandWithoutACollectionCreatesOne() {
    search.answer(
        "manageCollections", new CollectionSnapshot(collection, "Concerts", "", List.of()));

    auditedSearch.manageCollections(
        ana, new CollectionCommand(Optional.empty(), Optional.of("Concerts"), List.of()));

    AuditEntry entry = auditLog.only();
    assertThat(entry.action()).isEqualTo(AuditAction.COLLECTION_CREATED);
    assertThat(entry.resource()).contains(collection);
    assertThat(entry.detail()).isEqualTo("Concerts");
  }

  @Test
  void addingItemsToACollectionSaysHowMany() {
    search.answer(
        "manageCollections", new CollectionSnapshot(collection, "Concerts", "", List.of(file)));

    auditedSearch.manageCollections(
        ana, new CollectionCommand(Optional.of(collection), Optional.empty(), List.of(file)));

    AuditEntry entry = auditLog.only();
    assertThat(entry.action()).isEqualTo(AuditAction.COLLECTION_MODIFIED);
    assertThat(entry.detail()).isEqualTo("Concerts, 1 item added");
  }

  @Test
  void taggingIsRecordedWithTheTag() {
    auditedSearch.tagFile(ana, file, new Tag("live", TagSource.MANUAL));

    AuditEntry entry = auditLog.only();
    assertThat(entry.action()).isEqualTo(AuditAction.FILE_TAGGED);
    assertThat(entry.resource()).contains(file);
    assertThat(entry.detail()).isEqualTo("live");
  }

  @Test
  void searchingAndBrowsingRecordNothing() {
    search.answer("searchContent", List.of()).answer("browseLibrary", List.of());

    auditedSearch.searchContent(ana, "jazz");
    auditedSearch.browseLibrary(ana);
    auditedSearch.browseLibrary(ana, collection);

    assertThat(auditLog.entries).isEmpty();
  }

  @Test
  void anOfflineDownloadIsRecordedAndSyncingIsNot() {
    sync.answer("cacheContent", new OfflinePackage(file, List.of(), Path.of("/media/a.mp3")))
        .answer("hasPendingChanges", false);

    auditedSync.cacheContent(ana, file);
    auditedSync.hasPendingChanges(ana, Instant.EPOCH);

    AuditEntry entry = auditLog.only();
    assertThat(entry.action()).isEqualTo(AuditAction.FILE_DOWNLOADED);
    assertThat(entry.resource()).contains(file);
  }
}
