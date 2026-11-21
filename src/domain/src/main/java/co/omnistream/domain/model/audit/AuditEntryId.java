package co.omnistream.domain.model.audit;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of an entry of the audit trail.
 *
 * @param value the UUID behind the identifier
 */
public record AuditEntryId(UUID value) {

  public AuditEntryId {
    Objects.requireNonNull(value, "value");
  }

  /** A new, random identifier. */
  public static AuditEntryId random() {
    return new AuditEntryId(UUID.randomUUID());
  }
}
