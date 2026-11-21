package co.omnistream.domain.service.audit;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.OfflinePackage;
import co.omnistream.domain.port.dto.SyncDelta;
import co.omnistream.domain.port.in.ISync;
import co.omnistream.domain.port.out.IAuditLog;
import co.omnistream.domain.port.out.IUnitOfWork;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Records offline downloads in the audit trail, and passes every call on to the synchronization
 * service (Decorator, ADR 0025): a copy that leaves the server is worth knowing about.
 */
public final class AuditedSync extends AuditingDecorator implements ISync {
  private final ISync delegate;

  public AuditedSync(ISync delegate, IAuditLog auditLog, IUnitOfWork unitOfWork, Clock clock) {
    super(auditLog, unitOfWork, clock);
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  public SyncDelta syncChanges(UserId u, Instant since) {
    return delegate.syncChanges(u, since);
  }

  @Override
  public OfflinePackage cacheContent(UserId u, ItemId id) {
    return audited(
        () -> delegate.cacheContent(u, id),
        pack -> Optional.of(entry(u, AuditAction.FILE_DOWNLOADED).about(id)));
  }

  @Override
  public boolean hasPendingChanges(UserId u, Instant since) {
    return delegate.hasPendingChanges(u, since);
  }
}
