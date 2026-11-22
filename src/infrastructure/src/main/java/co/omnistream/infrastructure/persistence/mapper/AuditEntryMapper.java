package co.omnistream.infrastructure.persistence.mapper;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.model.audit.AuditEntryId;
import co.omnistream.domain.model.catalog.ItemId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Reads a row of {@code audit_entries} (Data Mapper). */
public final class AuditEntryMapper {

  /** The audit entry of the current row. */
  public AuditEntry toValue(ResultSet row) throws SQLException {
    return new AuditEntry(
        new AuditEntryId(UUID.fromString(row.getString("id"))),
        Instant.ofEpochMilli(row.getLong("happened_at")),
        new UserId(UUID.fromString(row.getString("actor"))),
        AuditAction.valueOf(row.getString("action")),
        optionalId(row.getString("resource")),
        optionalUser(row.getString("subject")),
        row.getString("detail"));
  }

  private static Optional<ItemId> optionalId(String value) {
    return Optional.ofNullable(value).map(v -> new ItemId(UUID.fromString(v)));
  }

  private static Optional<UserId> optionalUser(String value) {
    return Optional.ofNullable(value).map(v -> new UserId(UUID.fromString(v)));
  }
}
