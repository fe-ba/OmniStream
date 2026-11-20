package co.omnistream.domain.port.out;

import co.omnistream.domain.event.DomainEvent;

/** Driven port: defined by the domain, implemented by secondary adapters. */
public interface IEventPublisher {
  void publish(DomainEvent e);
}
