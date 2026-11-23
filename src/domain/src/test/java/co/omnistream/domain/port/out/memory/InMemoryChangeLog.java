package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.event.CatalogEvent;
import co.omnistream.domain.event.ChangeType;
import co.omnistream.domain.event.EventId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.SyncDelta;
import co.omnistream.domain.port.out.IChangeLog;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * In-memory change log for the domain tests (ADR 0018).
 *
 * <p>It keeps one entry per event identifier, as the real adapter does, and groups the changes
 * since an instant by their {@link ChangeType}.
 */
public final class InMemoryChangeLog implements IChangeLog {

  private final Map<EventId, CatalogEvent> events = new LinkedHashMap<>();

  @Override
  public void register(CatalogEvent event) {
    Objects.requireNonNull(event, "event");
    events.putIfAbsent(event.id(), event);
  }

  @Override
  public SyncDelta changesSince(Instant since) {
    Objects.requireNonNull(since, "since");
    Set<ItemId> added = new LinkedHashSet<>();
    Set<ItemId> modified = new LinkedHashSet<>();
    Set<ItemId> removed = new LinkedHashSet<>();
    for (CatalogEvent event : since(since)) {
      switch (event.changeType()) {
        case ADDED -> added.add(event.item());
        case MODIFIED -> modified.add(event.item());
        case REMOVED -> removed.add(event.item());
      }
    }
    return new SyncDelta(List.copyOf(added), List.copyOf(modified), List.copyOf(removed));
  }

  @Override
  public boolean hasChangesSince(Instant since) {
    Objects.requireNonNull(since, "since");
    return events.values().stream().anyMatch(event -> !event.occurredAt().isBefore(since));
  }

  private List<CatalogEvent> since(Instant since) {
    return events.values().stream()
        .filter(event -> !event.occurredAt().isBefore(since))
        .sorted(Comparator.comparing(CatalogEvent::occurredAt))
        .toList();
  }
}
