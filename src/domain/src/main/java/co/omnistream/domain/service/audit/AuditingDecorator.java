package co.omnistream.domain.service.audit;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.port.out.IAuditLog;
import co.omnistream.domain.port.out.IUnitOfWork;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Base of the audit decorators (Decorator, ADR 0025): runs a call of the wrapped service and
 * records what it did, both in one unit of work. If the call throws, nothing is recorded and the
 * exception goes on unchanged.
 */
public abstract class AuditingDecorator {
  protected final IAuditLog auditLog;
  protected final IUnitOfWork unitOfWork;
  protected final Clock clock;

  protected AuditingDecorator(IAuditLog auditLog, IUnitOfWork unitOfWork, Clock clock) {
    this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
    this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Runs {@code call} and records the entry {@code describe} makes from its result, if it makes
   * one: some calls only change something in some cases.
   */
  protected <T> T audited(Supplier<T> call, Function<? super T, Optional<AuditEntry>> describe) {
    return unitOfWork.execute(() -> {
      T result = call.get();
      describe.apply(result).ifPresent(auditLog::append);
      return result;
    });
  }

  /** Runs {@code call}, which returns nothing, and records the entry {@code describe} makes. */
  protected void audited(Runnable call, Supplier<AuditEntry> describe) {
    unitOfWork.execute(() -> {
      call.run();
      auditLog.append(describe.get());
      return null;
    });
  }

  /** Records {@code e} on its own, for something that happened outside a call that succeeded. */
  protected void record(AuditEntry e) {
    unitOfWork.execute(() -> {
      auditLog.append(e);
      return null;
    });
  }

  /** A new entry: {@code actor} did {@code action} now. */
  protected AuditEntry entry(UserId actor, AuditAction action) {
    return AuditEntry.of(actor, action, clock.instant());
  }
}
