package co.omnistream.domain.service;

import static co.omnistream.domain.model.access.AccessFixtures.item;
import static co.omnistream.domain.service.ServiceTestDoubles.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.event.FileDeleted;
import co.omnistream.domain.event.FileIngested;
import co.omnistream.domain.event.FileModified;
import co.omnistream.domain.service.ServiceTestDoubles.InMemoryOutbox;
import co.omnistream.domain.service.ServiceTestDoubles.RecordingEventPublisher;
import org.junit.jupiter.api.Test;

class EventDispatchServiceTest {

  private final InMemoryOutbox outbox = new InMemoryOutbox();
  private final RecordingEventPublisher publisher = new RecordingEventPublisher();
  private final EventDispatchService service = new EventDispatchService(outbox, publisher);

  @Test
  void publishPendingBatchesUpToLimitAndReturnsZeroWhenEmpty() {
    FileIngested first = new FileIngested(item(), NOW);
    FileModified second = new FileModified(item(), NOW.plusSeconds(1));
    FileDeleted third = new FileDeleted(item(), NOW.plusSeconds(2));
    outbox.enqueue(first);
    outbox.enqueue(second);
    outbox.enqueue(third);

    int firstBatch = service.publishPending(2);
    assertThat(firstBatch).isEqualTo(2);
    assertThat(publisher.published).containsExactly(first, second);
    assertThat(outbox.publishedIds).containsExactly(first.id(), second.id());
    assertThat(outbox.pendingCount()).isEqualTo(1);

    int secondBatch = service.publishPending(5);
    assertThat(secondBatch).isEqualTo(1);
    assertThat(publisher.published).containsExactly(first, second, third);
    assertThat(outbox.publishedIds).containsExactly(first.id(), second.id(), third.id());
    assertThat(outbox.pendingCount()).isZero();

    assertThat(service.publishPending(10)).isZero();
  }

  @Test
  void publishPendingLeavesFailedAndLaterEventsUnmarkedWhenPublisherFails() {
    FileIngested first = new FileIngested(item(), NOW);
    FileModified failing = new FileModified(item(), NOW.plusSeconds(1));
    FileDeleted third = new FileDeleted(item(), NOW.plusSeconds(2));
    outbox.enqueue(first);
    outbox.enqueue(failing);
    outbox.enqueue(third);
    publisher.failOn.add(failing.id());

    assertThatThrownBy(() -> service.publishPending(10)).isInstanceOf(IllegalStateException.class);

    assertThat(publisher.published).containsExactly(first);
    assertThat(outbox.publishedIds).containsExactly(first.id());
    assertThat(outbox.fetchPending(10)).containsExactly(failing, third);
  }

  @Test
  void publishPendingRejectsNonPositiveLimit() {
    assertThatThrownBy(() -> service.publishPending(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.publishPending(-3))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
