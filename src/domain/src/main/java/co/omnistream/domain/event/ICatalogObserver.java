package co.omnistream.domain.event;

/** Observer of catalog events, notified by the event bus (Observer). */
public interface ICatalogObserver {
  void onCatalogChanged(DomainEvent e);
}
