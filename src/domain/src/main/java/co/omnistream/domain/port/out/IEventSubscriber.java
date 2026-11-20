package co.omnistream.domain.port.out;

import co.omnistream.domain.event.ICatalogObserver;

/** Driven port: defined by the domain, implemented by secondary adapters. */
public interface IEventSubscriber {
  void subscribe(ICatalogObserver observer);

  void unsubscribe(ICatalogObserver observer);
}
