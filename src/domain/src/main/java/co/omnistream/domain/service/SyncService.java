package co.omnistream.domain.service;

import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.dto.OfflinePackage;
import co.omnistream.domain.port.dto.SyncDelta;
import co.omnistream.domain.port.in.ISync;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.IChangeLog;
import co.omnistream.domain.port.out.IFileStorage;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.time.Clock;
import java.time.Instant;

/** Domain service that implements the ISync driving port. */
public final class SyncService extends AuthorizedService implements ISync {
  private final IChangeLog changeLog;
  private final ICatalogRepository catalogRepository;
  private final IFileStorage storage;

  public SyncService(
      IUserRepository userRepository,
      AccessPolicy accessPolicy,
      Clock clock,
      IChangeLog changeLog,
      ICatalogRepository catalogRepository,
      IFileStorage storage) {
    super(userRepository, accessPolicy, clock);
    this.changeLog = changeLog;
    this.catalogRepository = catalogRepository;
    this.storage = storage;
  }

  @Override
  public SyncDelta syncChanges(UserId u, Instant since) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public OfflinePackage cacheContent(UserId u, ItemId id) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public boolean hasPendingChanges(UserId u, Instant since) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  private OfflinePackage pack(MediaFileSnapshot a) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  private SyncDelta filterVisible(User u, SyncDelta d) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
