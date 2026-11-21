package co.omnistream.domain.port.dto;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.model.catalog.ItemId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Which entries of the audit trail to read. Every filter that is present must match; an empty
 * filter matches everything. The newest entries come first, at most {@code limit} of them.
 *
 * <p>Filtering by {@code resource} gives the history of a file or a collection; by {@code actor},
 * what someone did; by {@code subject}, what was done to someone.
 *
 * @param actor who did it
 * @param resource the file or collection it was done on
 * @param subject the user it was done to
 * @param actions what was done; empty means any action
 * @param from earliest moment, included
 * @param to latest moment, excluded
 * @param limit how many entries at most, from 1 to {@link #MAX_LIMIT}
 */
public record AuditQuery(
    Optional<UserId> actor,
    Optional<ItemId> resource,
    Optional<UserId> subject,
    Set<AuditAction> actions,
    Optional<Instant> from,
    Optional<Instant> to,
    int limit) {

  /** Most entries one query returns. */
  public static final int MAX_LIMIT = 500;

  public AuditQuery {
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(subject, "subject");
    actions = Set.copyOf(actions);
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException(
          "An audit query returns from 1 to " + MAX_LIMIT + " entries, not " + limit);
    }
    if (from.isPresent() && to.isPresent() && !from.get().isBefore(to.get())) {
      throw new IllegalArgumentException("'from' must come before 'to'");
    }
  }

  /** The latest {@code limit} entries, without filters. */
  public static AuditQuery latest(int limit) {
    return new AuditQuery(
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Set.of(),
        Optional.empty(),
        Optional.empty(),
        limit);
  }

  /** The same query, only about {@code resource}: the history of one file or collection. */
  public AuditQuery about(ItemId resource) {
    return new AuditQuery(actor, Optional.of(resource), subject, actions, from, to, limit);
  }

  /** Whether {@code e} passes every filter. The limit is not a filter. */
  public boolean matches(AuditEntry e) {
    Objects.requireNonNull(e, "e");
    return actor.map(a -> a.equals(e.actor())).orElse(true)
        && resource.map(r -> e.resource().filter(r::equals).isPresent()).orElse(true)
        && subject.map(s -> e.subject().filter(s::equals).isPresent()).orElse(true)
        && (actions.isEmpty() || actions.contains(e.action()))
        && from.map(f -> !e.at().isBefore(f)).orElse(true)
        && to.map(t -> e.at().isBefore(t)).orElse(true);
  }
}
