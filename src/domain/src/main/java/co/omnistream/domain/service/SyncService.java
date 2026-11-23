package co.omnistream.domain.service;

import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.AccessType;
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
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

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
    this.changeLog = Objects.requireNonNull(changeLog, "changeLog");
    this.catalogRepository = Objects.requireNonNull(catalogRepository, "catalogRepository");
    this.storage = Objects.requireNonNull(storage, "storage");
  }

  @Override
  public SyncDelta syncChanges(UserId u, Instant since) {
    Objects.requireNonNull(since, "since");
    User user = requireActiveUser(u);
    return filterVisible(user, changeLog.changesSince(since));
  }

  @Override
  public OfflinePackage cacheContent(UserId u, ItemId id) {
    Objects.requireNonNull(id, "id");
    CatalogSnapshot catalog = catalogRepository.getCatalog().toSnapshot();
    checkAccess(u, id, AccessType.DOWNLOAD, catalog);
    MediaFileSnapshot file =
        catalogRepository
            .findFile(id)
            .orElseThrow(() -> new ResourceNotFound("File not found: " + id));
    if (!storage.exists(file.path())) {
      throw new ResourceNotFound("File content not found in storage: " + file.path());
    }
    return pack(file);
  }

  @Override
  public boolean hasPendingChanges(UserId u, Instant since) {
    Objects.requireNonNull(since, "since");
    User user = requireActiveUser(u);
    if (!changeLog.hasChangesSince(since)) {
      return false;
    }
    return !filterVisible(user, changeLog.changesSince(since)).isEmpty();
  }

  private User requireActiveUser(UserId u) {
    User user = loadUser(u);
    if (!user.toSnapshot().active()) {
      throw new AccessDenied("User account is deactivated");
    }
    return user;
  }

  private OfflinePackage pack(MediaFileSnapshot a) {
    return new OfflinePackage(a.id(), List.copyOf(a.metadata()), a.path());
  }

  private SyncDelta filterVisible(User u, SyncDelta d) {
    CatalogSnapshot catalog = catalogRepository.getCatalog().toSnapshot();
    List<ItemId> added =
        d.added().stream()
            .filter(id -> accessPolicy.authorizes(u, id, AccessType.VIEW, catalog))
            .toList();
    List<ItemId> modified =
        d.modified().stream()
            .filter(id -> accessPolicy.authorizes(u, id, AccessType.VIEW, catalog))
            .toList();
    List<ItemId> removed = List.copyOf(d.removed());
    return new SyncDelta(added, modified, removed);
  }
}
