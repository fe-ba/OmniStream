package co.omnistream.domain.port.in;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.OfflinePackage;
import co.omnistream.domain.port.dto.SyncDelta;
import java.time.Instant;

/** Driving port: implemented by a domain service, called by primary adapters. */
public interface ISync {
  SyncDelta syncChanges(UserId u, Instant since);

  OfflinePackage cacheContent(UserId u, ItemId id);

  boolean hasPendingChanges(UserId u, Instant since);
}
