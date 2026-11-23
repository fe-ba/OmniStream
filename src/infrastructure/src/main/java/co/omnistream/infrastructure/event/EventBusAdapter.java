package co.omnistream.infrastructure.event;

import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.event.ICatalogObserver;
import co.omnistream.domain.port.out.IEventBus;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Secondary adapter that carries the domain events to the observers (Observer, ADR 0026).
 *
 * <p>The bus is the only owner of the observer list. An observer that fails does not stop the
 * others: its failure is logged and the event goes on.
 */
public final class EventBusAdapter implements IEventBus {

  private static final System.Logger LOG = System.getLogger(EventBusAdapter.class.getName());

  private final CopyOnWriteArrayList<ICatalogObserver> observers = new CopyOnWriteArrayList<>();

  @Override
  public void publish(DomainEvent e) {
    Objects.requireNonNull(e, "e");
    for (ICatalogObserver observer : observers) {
      try {
        observer.onCatalogChanged(e);
      } catch (RuntimeException failure) {
        LOG.log(
            System.Logger.Level.WARNING,
            "Observer " + observer.getClass().getName() + " failed with " + e.type(),
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
