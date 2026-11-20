package co.omnistream.domain.service;

import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.event.ICatalogObserver;
import co.omnistream.domain.port.out.IChangeLog;

/** Concrete observer that writes catalog changes to the change log (Observer). */
public final class SyncObserver implements ICatalogObserver {
  private final IChangeLog changeLog;

  public SyncObserver(IChangeLog changeLog) {
    this.changeLog = changeLog;
  }

  @Override
  public void onCatalogChanged(DomainEvent e) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
