package co.omnistream.domain.port.out.contract;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.event.CollectionCreated;
import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.event.ICatalogObserver;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.out.IEventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behaviour every {@link IEventBus} must share (ADR 0018). */
public abstract class EventBusContract {

  protected abstract IEventBus createBus();

  @Test
  void publishesToEveryObserver() {
    IEventBus bus = createBus();
    List<DomainEvent> first = new ArrayList<>();
    List<DomainEvent> second = new ArrayList<>();
    bus.subscribe(first::add);
    bus.subscribe(second::add);

    bus.publish(event());

    assertThat(first).hasSize(1);
    assertThat(second).hasSize(1);
  }

  @Test
  void notifiesAnObserverOnlyOnce() {
    IEventBus bus = createBus();
    List<DomainEvent> received = new ArrayList<>();
    ICatalogObserver observer = received::add;
    bus.subscribe(observer);
    bus.subscribe(observer);

    bus.publish(event());

    assertThat(received).hasSize(1);
  }

  @Test
  void stopsNotifyingAnObserverThatWasRemoved() {
    IEventBus bus = createBus();
    List<DomainEvent> received = new ArrayList<>();
    ICatalogObserver observer = received::add;
    bus.subscribe(observer);

    bus.unsubscribe(observer);
    bus.publish(event());

    assertThat(received).isEmpty();
  }

  @Test
  void oneFailingObserverDoesNotStopTheOthers() {
    IEventBus bus = createBus();
    List<DomainEvent> received = new ArrayList<>();
    bus.subscribe(event -> {
      throw new IllegalStateException("boom");
    });
    bus.subscribe(received::add);

    bus.publish(event());

    assertThat(received).hasSize(1);
  }

  @Test
  void publishingWithoutObserversDoesNothing() {
    createBus().publish(event());
  }

  private static DomainEvent event() {
    return new CollectionCreated(new ItemId(UUID.randomUUID()), PortFixtures.NOW);
  }
}
