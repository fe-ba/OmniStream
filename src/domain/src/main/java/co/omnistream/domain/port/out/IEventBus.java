package co.omnistream.domain.port.out;

/** Driven port: event bus that publishes events and keeps the observers (Observer). */
public interface IEventBus extends IEventPublisher, IEventSubscriber {}
