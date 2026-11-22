package co.omnistream.domain.service;

import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.FileRejected;
import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.IngestionResult;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaFile;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.model.catalog.MetadataSource;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.policy.DeduplicationPolicy;
import co.omnistream.domain.port.dto.ServerStatus;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.IConfigurationRepository;
import co.omnistream.domain.port.out.IDataBackup;
import co.omnistream.domain.port.out.IFileStorage;
import co.omnistream.domain.port.out.ISessionRepository;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.service.ingestion.IngestionContext;
import co.omnistream.domain.service.ingestion.IngestionStep;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import co.omnistream.domain.snapshot.UserSnapshot;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Domain service that implements the ICatalogManagement driving port. */
public final class CatalogService extends AuthorizedService implements ICatalogManagement {
  private final IngestionStep ingestionChain;
  private final ICatalogRepository catalogRepository;
  private final ISessionRepository sessionRepository;
  private final IFileStorage storage;
  private final IDataBackup dataBackup;
  private final IConfigurationRepository configurationRepository;
  private final DeduplicationPolicy deduplicationPolicy;
  private final IUnitOfWork unitOfWork;

  public CatalogService(
      IUserRepository userRepository,
      AccessPolicy accessPolicy,
      Clock clock,
      IngestionStep ingestionChain,
      ICatalogRepository catalogRepository,
      ISessionRepository sessionRepository,
      IFileStorage storage,
      IDataBackup dataBackup,
      IConfigurationRepository configurationRepository,
      DeduplicationPolicy deduplicationPolicy,
      IUnitOfWork unitOfWork) {
    super(userRepository, accessPolicy, clock);
    this.ingestionChain = Objects.requireNonNull(ingestionChain, "ingestionChain");
    this.catalogRepository = Objects.requireNonNull(catalogRepository, "catalogRepository");
    this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository");
    this.storage = Objects.requireNonNull(storage, "storage");
    this.dataBackup = Objects.requireNonNull(dataBackup, "dataBackup");
    this.configurationRepository =
        Objects.requireNonNull(configurationRepository, "configurationRepository");
    this.deduplicationPolicy = Objects.requireNonNull(deduplicationPolicy, "deduplicationPolicy");
    this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
  }

  /**
   * Access is denied by default ({@code AccessPolicy}): a regular user without a permission on the
   * new file could not even see it. When a file is indexed, grant its uploader {@code
   * PermissionLevel.MANAGE} on it ({@code user.grant}, then save the user) in the same unit of
   * work. The same applies to {@link #uploadFile}.
   */
  @Override
  public IngestionResult ingestFile(UserId u, Path path) {
    Objects.requireNonNull(path, "path");
    requireActiveNonGuest(u);
    return unitOfWork.execute(() -> {
      User uploader = loadUser(u);
      CatalogIndex catalog = catalogRepository.getCatalog();
      IngestionContext ctx = new IngestionContext();
      ctx.catalog = catalog;
      ctx.path = path;
      IngestionResult result = ingest(ctx);
      if (result.wasIndexed()) {
        catalogRepository.save(catalog);
        if (uploader.toSnapshot().role() == Role.USER) {
          uploader.grant(new Permission(result.fileId(), PermissionLevel.MANAGE));
          userRepository.save(uploader);
        }
      }
      return result;
    });
  }

  @Override
  public IngestionResult uploadFile(UserId u, String fileName, InputStream data) {
    Objects.requireNonNull(fileName, "fileName");
    Objects.requireNonNull(data, "data");
    requireActiveNonGuest(u);
    String cleanName = validateUploadFileName(fileName);
    Path libraryRoot = configurationRepository.getLibraryPath().normalize();
    Path target = libraryRoot.resolve(cleanName).normalize();
    if (!libraryRoot.equals(target.getParent())) {
      throw new FileRejected("Invalid upload target for file name: " + fileName);
    }
    boolean existedBefore = storage.exists(target);
    storage.write(target, data);
    try {
      return ingestFile(u, target);
    } catch (RuntimeException failure) {
      if (!existedBefore) {
        storage.delete(target);
      }
      throw failure;
    }
  }

  @Override
  public int reindexLibrary(UserId u, Path root) {
    Objects.requireNonNull(root, "root");
    requireAdmin(u);
    List<Path> paths = storage.list(root);
    return unitOfWork.execute(() -> {
      CatalogIndex catalog = catalogRepository.getCatalog();
      int indexed = 0;
      for (Path path : paths) {
        IngestionContext ctx = new IngestionContext();
        ctx.catalog = catalog;
        ctx.path = path;
        try {
          IngestionResult result = ingest(ctx);
          if (result != null && result.wasIndexed()) {
            indexed++;
          }
        } catch (FileRejected ignored) {
          // Unsupported files inside a scanned folder are skipped during reindex.
        }
      }
      if (indexed > 0) {
        catalogRepository.save(catalog);
      }
      return indexed;
    });
  }

  @Override
  public List<MediaFileSnapshot> detectDuplicates(UserId u) {
    User user = loadUser(u);
    if (!user.toSnapshot().active()) {
      throw new AccessDenied("User account is deactivated");
    }
    CatalogIndex catalog = catalogRepository.getCatalog();
    CatalogSnapshot snapshot = catalog.toSnapshot();
    Set<Fingerprint> duplicateFingerprints = new HashSet<>();
    for (MediaFileSnapshot file : snapshot.files()) {
      catalog
          .findItem(file.id())
          .filter(MediaFile.class::isInstance)
          .map(MediaFile.class::cast)
          .filter(mediaFile -> deduplicationPolicy.isDuplicate(mediaFile, catalog))
          .ifPresent(mediaFile -> duplicateFingerprints.add(file.fingerprint()));
    }
    return snapshot.files().stream()
        .filter(file -> duplicateFingerprints.contains(file.fingerprint()))
        .filter(file -> accessPolicy.authorizes(user, file.id(), AccessType.VIEW, snapshot))
        .toList();
  }

  @Override
  public void editMetadata(UserId u, ItemId id, String key, String value) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
    MetadataEntry entry = new MetadataEntry(key, value, MetadataSource.MANUAL);
    unitOfWork.execute(() -> {
      CatalogIndex catalog = catalogRepository.getCatalog();
      checkAccess(u, id, AccessType.MODIFY, catalog.toSnapshot());
      catalog.editMetadata(id, entry, clock.instant());
      catalogRepository.save(catalog);
      return null;
    });
  }

  @Override
  public void deleteFile(UserId u, ItemId id) {
    Objects.requireNonNull(id, "id");
    unitOfWork.execute(() -> {
      CatalogIndex catalog = catalogRepository.getCatalog();
      checkAccess(u, id, AccessType.DELETE, catalog.toSnapshot());
      catalog.delete(id, clock.instant());
      catalogRepository.save(catalog);
      return null;
    });
  }

  @Override
  public Path backupCatalog(UserId admin, Path destination) {
    Objects.requireNonNull(destination, "destination");
    requireAdmin(admin);
    return dataBackup.backup(destination);
  }

  @Override
  public void configureLibraryPath(UserId admin, Path path) {
    Objects.requireNonNull(path, "path");
    requireAdmin(admin);
    configurationRepository.saveLibraryPath(path);
  }

  @Override
  public ServerStatus monitorServerStatus(UserId admin) {
    requireAdmin(admin);
    int indexedFiles = catalogRepository.getCatalog().totalFiles();
    long freeSpace = storage.freeSpace();
    int activeSessions = sessionRepository.countActive();
    return new ServerStatus(indexedFiles, freeSpace, activeSessions);
  }

  private IngestionResult ingest(IngestionContext ctx) {
    return ingestionChain.execute(ctx);
  }

  private void requireActiveNonGuest(UserId u) {
    UserSnapshot snapshot = loadUser(u).toSnapshot();
    if (!snapshot.active() || snapshot.role() == Role.GUEST) {
      throw new AccessDenied("Only active non-guest users can add files to the catalog");
    }
  }

  private static String validateUploadFileName(String fileName) {
    String clean = fileName.strip();
    if (clean.isEmpty()
        || ".".equals(clean)
        || "..".equals(clean)
        || clean.contains("/")
        || clean.contains("\\")) {
      throw new FileRejected("Invalid upload file name: " + fileName);
    }
    return clean;
  }
}
