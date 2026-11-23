package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.event.ICatalogObserver;
import co.omnistream.domain.port.out.IEventBus;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory event bus for the domain tests (ADR 0018).
 *
 * <p>Like the real adapter, it keeps one copy of each observer and an observer that fails does not
 * stop the others.
 */
public final class InMemoryEventBus implements IEventBus {

  private static final System.Logger LOG = System.getLogger(InMemoryEventBus.class.getName());

  private final CopyOnWriteArrayList<ICatalogObserver> observers = new CopyOnWriteArrayList<>();

  @Override
  public void publish(DomainEvent event) {
    Objects.requireNonNull(event, "event");
    for (ICatalogObserver observer : observers) {
      try {
        observer.onCatalogChanged(event);
      } catch (RuntimeException failure) {
        LOG.log(
            System.Logger.Level.WARNING,
            "Observer " + observer.getClass().getName() + " failed with " + event.type(),
            failure);
      }
    }
  }

  @Override
  public void subscribe(ICatalogObserver observer) {
    observers.addIfAbsent(Objects.requireNonNull(observer, "observer"));
  }

  @Override
  public void unsubscribe(ICatalogObserver observer) {
    observers.remove(Objects.requireNonNull(observer, "observer"));
  }
}
