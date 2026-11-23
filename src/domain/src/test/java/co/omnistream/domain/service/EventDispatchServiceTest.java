package co.omnistream.domain.service;

import static co.omnistream.domain.model.access.AccessFixtures.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.event.FileDeleted;
import co.omnistream.domain.event.FileIngested;
import co.omnistream.domain.event.FileModified;
import co.omnistream.domain.port.out.IEventPublisher;
import co.omnistream.domain.port.out.memory.InMemoryEventBus;
import co.omnistream.domain.port.out.memory.InMemoryOutboxRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventDispatchServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-21T18:00:00Z");

  private final InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
  private final InMemoryEventBus eventBus = new InMemoryEventBus();

  @Test
  void publishPendingBatchesUpToLimitAndReturnsZeroWhenEmpty() {
    List<DomainEvent> received = new ArrayList<>();
    eventBus.subscribe(received::add);
    EventDispatchService service = new EventDispatchService(outbox, eventBus);

    FileIngested first = new FileIngested(item(), NOW);
    FileModified second = new FileModified(item(), NOW.plusSeconds(1));
    FileDeleted third = new FileDeleted(item(), NOW.plusSeconds(2));
    outbox.add(first);
    outbox.add(second);
    outbox.add(third);

    int firstBatch = service.publishPending(2);
    assertThat(firstBatch).isEqualTo(2);
    assertThat(received).containsExactly(first, second);
    assertThat(outbox.published()).containsExactlyInAnyOrder(first.id(), second.id());
    assertThat(outbox.pending()).hasSize(1);

    int secondBatch = service.publishPending(5);
    assertThat(secondBatch).isEqualTo(1);
    assertThat(received).containsExactly(first, second, third);
    assertThat(outbox.published()).containsExactlyInAnyOrder(first.id(), second.id(), third.id());
    assertThat(outbox.pending()).isEmpty();

    assertThat(service.publishPending(10)).isZero();
  }

  @Test
  void publishPendingLeavesFailedAndLaterEventsUnmarkedWhenPublisherFails() {
    FileIngested first = new FileIngested(item(), NOW);
    FileModified failing = new FileModified(item(), NOW.plusSeconds(1));
    FileDeleted third = new FileDeleted(item(), NOW.plusSeconds(2));
    outbox.add(first);
    outbox.add(failing);
    outbox.add(third);

    List<DomainEvent> received = new ArrayList<>();
    IEventPublisher failingPublisher =
        event -> {
          if (event.id().equals(failing.id())) {
            throw new IllegalStateException("broker down");
          }
          received.add(event);
        };
    EventDispatchService service = new EventDispatchService(outbox, failingPublisher);

    assertThatThrownBy(() -> service.publishPending(10)).isInstanceOf(IllegalStateException.class);

    assertThat(received).containsExactly(first);
    assertThat(outbox.published()).containsExactly(first.id());
    assertThat(outbox.fetchPending(10)).containsExactly(failing, third);
  }

  @Test
  void publishPendingRejectsNonPositiveLimit() {
    EventDispatchService service = new EventDispatchService(outbox, eventBus);
    assertThatThrownBy(() -> service.publishPending(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.publishPending(-3))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
