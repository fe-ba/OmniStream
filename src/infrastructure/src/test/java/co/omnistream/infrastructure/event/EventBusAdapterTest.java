package co.omnistream.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.event.CollectionCreated;
import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.event.ICatalogObserver;
import co.omnistream.domain.model.catalog.ItemId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventBusAdapterTest {

  @Test
  void publishesToEveryObserver() {
    EventBusAdapter bus = new EventBusAdapter();
    List<DomainEvent> first = new ArrayList<>();
    List<DomainEvent> second = new ArrayList<>();
    bus.subscribe(first::add);
    bus.subscribe(second::add);

    bus.publish(event());

    assertThat(first).hasSize(1);
    assertThat(second).hasSize(1);
  }

  @Test
  void stopsNotifyingAnObserverThatWasRemoved() {
    EventBusAdapter bus = new EventBusAdapter();
    List<DomainEvent> received = new ArrayList<>();
    ICatalogObserver observer = received::add;
    bus.subscribe(observer);
    bus.unsubscribe(observer);

    bus.publish(event());

    assertThat(received).isEmpty();
  }

  @Test
  void oneFailingObserverDoesNotStopTheOthers() {
    EventBusAdapter bus = new EventBusAdapter();
    List<DomainEvent> received = new ArrayList<>();
    bus.subscribe(event -> {
      throw new IllegalStateException("boom");
    });
    bus.subscribe(received::add);

    bus.publish(event());

    assertThat(received).hasSize(1);
  }

  private static DomainEvent event() {
    return new CollectionCreated(
        new ItemId(UUID.randomUUID()), Instant.parse("2026-01-01T10:00:00Z"));
  }
}
