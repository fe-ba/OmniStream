package co.omnistream.domain.service.audit;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.TextDocument;
import co.omnistream.domain.port.in.ITextEditing;
import co.omnistream.domain.port.out.IAuditLog;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Records the saves of the text editor (ADR 0025, ADR 0027). Everyone editing a file saves it a
 * couple of seconds after the last change, so most saves repeat the text already stored: only a
 * save that changed the file is recorded, as {@link AuditAction#FILE_EDITED}. Opening a file is a
 * read and is not recorded.
 */
public final class AuditedTextEditing extends AuditingDecorator implements ITextEditing {
  private final ITextEditing delegate;
  private final ICatalogRepository catalogRepository;

  public AuditedTextEditing(
      ITextEditing delegate,
      ICatalogRepository catalogRepository,
      IAuditLog auditLog,
      IUnitOfWork unitOfWork,
      Clock clock) {
    super(auditLog, unitOfWork, clock);
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.catalogRepository = Objects.requireNonNull(catalogRepository, "catalogRepository");
  }

  @Override
  public TextDocument open(UserId u, ItemId file) {
    return delegate.open(u, file);
  }

  /**
   * Compares the fingerprint stored before the save with the one it answers, in the same unit of
   * work as the save, so two saves at once cannot both look like the first.
   */
  @Override
  public TextDocument save(UserId u, ItemId file, String text, Fingerprint basedOn) {
    return unitOfWork.execute(() -> {
      Optional<Fingerprint> before =
          catalogRepository.findFile(file).map(MediaFileSnapshot::fingerprint);
      TextDocument saved = delegate.save(u, file, text, basedOn);
      edited(u, saved, before).ifPresent(auditLog::append);
      return saved;
    });
  }

  private Optional<AuditEntry> edited(UserId u, TextDocument saved, Optional<Fingerprint> before) {
    if (before.filter(saved.fingerprint()::matches).isPresent()) {
      return Optional.empty();
    }
    return Optional.of(
        entry(u, AuditAction.FILE_EDITED)
            .about(saved.file())
            .saying(saved.name() + ", " + saved.text().length() + " characters"));
  }
}
