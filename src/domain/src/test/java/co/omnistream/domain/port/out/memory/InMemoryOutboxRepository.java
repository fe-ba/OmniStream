package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.event.EventId;
import co.omnistream.domain.port.out.IOutboxRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * In-memory outbox for the domain tests (ADR 0018).
 *
 * <p>Tests add the events they need with {@link #add(DomainEvent)}; a catalog repository wired to
 * this outbox adds them when it saves the catalog, as the JDBC adapter does.
 */
public final class InMemoryOutboxRepository implements IOutboxRepository {

  private final Map<EventId, DomainEvent> pending = new LinkedHashMap<>();
  private final Set<EventId> published = new LinkedHashSet<>();

  /** Adds an event to the outbox, as the persistence adapter does with a pulled event. */
  public void add(DomainEvent event) {
    Objects.requireNonNull(event, "event");
    pending.putIfAbsent(event.id(), event);
  }

  /** Adds every event, keeping the order in which they are given. */
  public void addAll(Collection<? extends DomainEvent> events) {
    Objects.requireNonNull(events, "events");
    events.forEach(this::add);
  }

  /** The events that were pending when they were marked, for the assertions of a test. */
  public Set<EventId> published() {
    return Set.copyOf(published);
  }

  /** The events that have not been published yet, without a limit. */
  public List<DomainEvent> pending() {
    return List.copyOf(pending.values());
  }

  @Override
  public List<DomainEvent> fetchPending(int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("A limit must be positive");
    }
    return pending.values().stream()
        .sorted(Comparator.comparing(DomainEvent::occurredAt))
        .limit(limit)
        .toList();
  }

  @Override
  public void markPublished(EventId id) {
    Objects.requireNonNull(id, "id");
    if (pending.remove(id) != null) {
      published.add(id);
    }
  }
}
