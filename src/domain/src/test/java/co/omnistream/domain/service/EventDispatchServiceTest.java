package co.omnistream.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.event.CollectionCreated;
import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.event.EventId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.out.IOutboxRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventDispatchServiceTest {

  @Test
  void publishesThePendingEventsAndMarksThem() {
    InMemoryOutbox outbox = new InMemoryOutbox();
    List<DomainEvent> published = new ArrayList<>();
    DomainEvent first = event();
    DomainEvent second = event();
    outbox.pending.add(first);
    outbox.pending.add(second);
    EventDispatchService service = new EventDispatchService(outbox, published::add);

    assertThat(service.publishPending(10)).isEqualTo(2);
    assertThat(published).containsExactly(first, second);
    assertThat(outbox.pending).isEmpty();
    assertThat(outbox.marked).containsExactly(first.id(), second.id());
  }

  @Test
  void leavesAnEventPendingWhenTheBusFails() {
    InMemoryOutbox outbox = new InMemoryOutbox();
    DomainEvent event = event();
    outbox.pending.add(event);
    EventDispatchService service =
        new EventDispatchService(
            outbox,
            e -> {
              throw new IllegalStateException("bus down");
            });

    assertThatThrownBy(() -> service.publishPending(10)).isInstanceOf(IllegalStateException.class);
    assertThat(outbox.pending).containsExactly(event);
    assertThat(outbox.marked).isEmpty();
  }

  @Test
  void refusesANonPositiveLimit() {
    EventDispatchService service = new EventDispatchService(new InMemoryOutbox(), e -> {});

    assertThatThrownBy(() -> service.publishPending(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static DomainEvent event() {
    return new CollectionCreated(
        new ItemId(UUID.randomUUID()), Instant.parse("2026-01-01T10:00:00Z"));
  }

  private static final class InMemoryOutbox implements IOutboxRepository {
    private final List<DomainEvent> pending = new ArrayList<>();
    private final List<EventId> marked = new ArrayList<>();

    @Override
    public List<DomainEvent> fetchPending(int limit) {
      return List.copyOf(pending.subList(0, Math.min(limit, pending.size())));
    }

    @Override
    public void markPublished(EventId id) {
      marked.add(id);
      pending.removeIf(event -> event.id().equals(id));
    }
  }
}
