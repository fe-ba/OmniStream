package co.omnistream.domain.port.out;

import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.port.dto.AuditQuery;
import java.util.List;

/**
 * Driven port: where the audit trail is kept (ADR 0025).
 *
 * <p>The trail only grows: there is no way to change or delete an entry through this port.
 */
public interface IAuditLog {
  /**
   * Keeps {@code e}. Called inside the unit of work of the change it describes, so the persistence
   * adapter writes both in the same transaction.
   */
  void append(AuditEntry e);

  /** The entries that match {@code q}, newest first, at most {@code q.limit()} of them. */
  List<AuditEntry> find(AuditQuery q);
}
