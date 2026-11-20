package co.omnistream.domain.port.out;

import co.omnistream.domain.event.CatalogEvent;
import co.omnistream.domain.port.dto.SyncDelta;
import java.time.Instant;

/** Driven port: defined by the domain, implemented by secondary adapters. */
public interface IChangeLog {
  void register(CatalogEvent e);

  SyncDelta changesSince(Instant since);

  boolean hasChangesSince(Instant since);
}
