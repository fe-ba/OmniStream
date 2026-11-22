package co.omnistream.domain.service;

import co.omnistream.domain.event.CatalogEvent;
import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.event.ICatalogObserver;
import co.omnistream.domain.port.out.IChangeLog;
import java.util.Objects;

/** Concrete observer that writes catalog changes to the change log (Observer). */
public final class SyncObserver implements ICatalogObserver {
  private final IChangeLog changeLog;

  public SyncObserver(IChangeLog changeLog) {
    this.changeLog = Objects.requireNonNull(changeLog, "changeLog");
  }

  @Override
  public void onCatalogChanged(DomainEvent e) {
    Objects.requireNonNull(e, "event");
    if (e instanceof CatalogEvent event) {
      changeLog.register(event);
    }
  }
}
