package co.omnistream.domain.service.audit;

import static co.omnistream.domain.service.audit.AuditSupport.CLOCK;
import static co.omnistream.domain.service.audit.AuditSupport.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.exception.VersionConflict;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.IndexingStatus;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.port.dto.TextDocument;
import co.omnistream.domain.port.in.ITextEditing;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.service.audit.AuditSupport.FakePort;
import co.omnistream.domain.service.audit.AuditSupport.InMemoryAuditLog;
import co.omnistream.domain.service.audit.AuditSupport.TrackingUnitOfWork;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditedTextEditingTest {

  private static final Fingerprint OLD = new Fingerprint("SHA-256", "a".repeat(64));
  private static final Fingerprint NEW = new Fingerprint("SHA-256", "b".repeat(64));

  private final TrackingUnitOfWork unitOfWork = new TrackingUnitOfWork();
  private final InMemoryAuditLog auditLog = new InMemoryAuditLog(unitOfWork);
  private final FakePort<ITextEditing> service = new FakePort<>(ITextEditing.class);
  private final FakePort<ICatalogRepository> catalog = new FakePort<>(ICatalogRepository.class);
  private final ITextEditing audited =
      new AuditedTextEditing(service.port, catalog.port, auditLog, unitOfWork, CLOCK);

  private final UserId ana = new UserId(UUID.randomUUID());
  private final ItemId file = new ItemId(UUID.randomUUID());

  private MediaFileSnapshot stored(Fingerprint h) {
    return new MediaFileSnapshot(
        file,
        "setlist.md",
        Path.of("/media/setlist.md"),
        h,
        MediaType.DOCUMENT,
        40,
        Duration.ZERO,
        IndexingStatus.INDEXED,
        NOW,
        List.of(),
        Set.of());
  }

  private TextDocument document(String text, Fingerprint h) {
    return new TextDocument(file, "setlist.md", text, h, true);
  }

  @Test
  void aSaveThatChangesTheFileIsRecorded() {
    catalog.answer("findFile", Optional.of(stored(OLD)));
    service.answer("save", document("# Setlist\n1. Intro\n", NEW));

    TextDocument saved = audited.save(ana, file, "# Setlist\n1. Intro\n", OLD);

    assertThat(saved.fingerprint()).isEqualTo(NEW);
    AuditEntry entry = auditLog.only();
    assertThat(entry.actor()).isEqualTo(ana);
    assertThat(entry.action()).isEqualTo(AuditAction.FILE_EDITED);
    assertThat(entry.resource()).contains(file);
    assertThat(entry.detail()).isEqualTo("setlist.md, 19 characters");
    assertThat(entry.at()).isEqualTo(NOW);
  }

  @Test
  void aSaveOfTheStoredTextIsNotRecorded() {
    catalog.answer("findFile", Optional.of(stored(OLD)));
    service.answer("save", document("same", OLD));

    audited.save(ana, file, "same", NEW);

    assertThat(auditLog.entries).isEmpty();
  }

  @Test
  void aFailedSaveIsNotRecorded() {
    catalog.answer("findFile", Optional.of(stored(OLD)));
    service.answer("save", new VersionConflict("changed"));

    assertThatThrownBy(() -> audited.save(ana, file, "mine", NEW))
        .isInstanceOf(VersionConflict.class);
    assertThat(auditLog.entries).isEmpty();
  }

  @Test
  void openingIsAReadAndIsNotRecorded() {
    service.answer("open", document("text", OLD));

    assertThat(audited.open(ana, file).text()).isEqualTo("text");
    assertThat(auditLog.entries).isEmpty();
    assertThat(catalog.calls).isEmpty();
  }
}
