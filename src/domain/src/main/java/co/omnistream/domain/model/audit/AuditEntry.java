package co.omnistream.domain.model.audit;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One line of the audit trail: who did what, when, on which file or collection, and to whom.
 *
 * <p>An entry is a value: it is written once and never changed (ADR 0025). Build one with {@link
 * #of} and add what applies with {@link #about}, {@link #concerning} and {@link #saying}.
 *
 * @param id identifier of the entry
 * @param at when it happened
 * @param actor who did it
 * @param action what they did
 * @param resource the file or collection it was done on, if any
 * @param subject the user it was done to, if any: whose role changed, who got a permission
 * @param detail what else is worth reading, in a few words: a level, a file name, a path. Never a
 *     password. Empty if there is nothing to add.
 */
public record AuditEntry(
    AuditEntryId id,
    Instant at,
    UserId actor,
    AuditAction action,
    Optional<ItemId> resource,
    Optional<UserId> subject,
    String detail) {

  /** Longest detail an entry keeps; {@link #saying} cuts longer ones. */
  public static final int MAX_DETAIL = 500;

  private static final String CUT = "...";

  public AuditEntry {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(at, "at");
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(detail, "detail");
    if (detail.length() > MAX_DETAIL) {
      throw new IllegalArgumentException(
          "An audit detail has at most " + MAX_DETAIL + " characters, not " + detail.length());
    }
  }

  /** A new entry: {@code actor} did {@code action} at {@code at}, with nothing else to say. */
  public static AuditEntry of(UserId actor, AuditAction action, Instant at) {
    return new AuditEntry(
        AuditEntryId.random(), at, actor, action, Optional.empty(), Optional.empty(), "");
  }

  /** The same entry, done on {@code resource}. */
  public AuditEntry about(ItemId resource) {
    return new AuditEntry(id, at, actor, action, Optional.of(resource), subject, detail);
  }

  /** The same entry, done to the user {@code subject}. */
  public AuditEntry concerning(UserId subject) {
    return new AuditEntry(id, at, actor, action, resource, Optional.of(subject), detail);
  }

  /**
   * The same entry with {@code detail}, cut to {@link #MAX_DETAIL} characters (ending in "...") if
   * it is longer: a long metadata value should not stop the change from being recorded.
   */
  public AuditEntry saying(String detail) {
    Objects.requireNonNull(detail, "detail");
    String kept =
        detail.length() <= MAX_DETAIL
            ? detail
            : detail.substring(0, MAX_DETAIL - CUT.length()) + CUT;
    return new AuditEntry(id, at, actor, action, resource, subject, kept);
  }
}
