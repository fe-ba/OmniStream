package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.port.dto.AuditQuery;
import co.omnistream.domain.port.out.IAuditLog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** In-memory audit trail for the domain tests (ADR 0018). */
public final class InMemoryAuditLog implements IAuditLog {

  private final List<AuditEntry> entries = new ArrayList<>();

  @Override
  public void append(AuditEntry entry) {
    entries.add(Objects.requireNonNull(entry, "entry"));
  }

  @Override
  public List<AuditEntry> find(AuditQuery query) {
    Objects.requireNonNull(query, "query");
    return entries.stream()
        .filter(query::matches)
        .sorted(Comparator.comparing(AuditEntry::at).reversed())
        .limit(query.limit())
        .toList();
  }

  /** Every entry kept, in the order in which it was appended. */
  public List<AuditEntry> entries() {
    return List.copyOf(entries);
  }
}
