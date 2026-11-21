package co.omnistream.domain.service.audit;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.model.catalog.IngestionResult;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.ServerStatus;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.domain.port.out.IAuditLog;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Records files added, edited and deleted, and the administration of the library, in the audit
 * trail, and passes every call on to the catalog service (Decorator, ADR 0025).
 */
public final class AuditedCatalogManagement extends AuditingDecorator
    implements ICatalogManagement {
  private final ICatalogManagement delegate;
  private final ICatalogRepository catalogRepository;

  public AuditedCatalogManagement(
      ICatalogManagement delegate,
      ICatalogRepository catalogRepository,
      IAuditLog auditLog,
      IUnitOfWork unitOfWork,
      Clock clock) {
    super(auditLog, unitOfWork, clock);
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.catalogRepository = Objects.requireNonNull(catalogRepository, "catalogRepository");
  }

  @Override
  public IngestionResult ingestFile(UserId u, Path path) {
    return audited(() -> delegate.ingestFile(u, path), result -> added(u, result, path.toString()));
  }

  @Override
  public IngestionResult uploadFile(UserId u, String fileName, InputStream data) {
    return audited(
        () -> delegate.uploadFile(u, fileName, data), result -> added(u, result, fileName));
  }

  @Override
  public int reindexLibrary(UserId u, Path root) {
    return audited(
        () -> delegate.reindexLibrary(u, root),
        indexed ->
            Optional.of(
                entry(u, AuditAction.LIBRARY_REINDEXED).saying(indexed + " files in " + root)));
  }

  @Override
  public List<MediaFileSnapshot> detectDuplicates(UserId u) {
    return delegate.detectDuplicates(u);
  }

  @Override
  public void editMetadata(UserId u, ItemId id, String key, String value) {
    audited(
        () -> delegate.editMetadata(u, id, key, value),
        () -> entry(u, AuditAction.METADATA_EDITED).about(id).saying(key + " = " + value));
  }

  /** Keeps the file's name in the entry: once it is deleted, the catalog no longer has it. */
  @Override
  public void deleteFile(UserId u, ItemId id) {
    String name = catalogRepository.findFile(id).map(MediaFileSnapshot::name).orElse("");
    audited(
        () -> delegate.deleteFile(u, id),
        () -> entry(u, AuditAction.FILE_DELETED).about(id).saying(name));
  }

  @Override
  public Path backupCatalog(UserId admin, Path destination) {
    return audited(
        () -> delegate.backupCatalog(admin, destination),
        backup -> Optional.of(entry(admin, AuditAction.BACKUP_CREATED).saying(backup.toString())));
  }

  @Override
  public void configureLibraryPath(UserId admin, Path path) {
    audited(
        () -> delegate.configureLibraryPath(admin, path),
        () -> entry(admin, AuditAction.LIBRARY_PATH_CHANGED).saying(path.toString()));
  }

  @Override
  public ServerStatus monitorServerStatus(UserId admin) {
    return delegate.monitorServerStatus(admin);
  }

  /** A rejected duplicate added nothing; any other result is a file in the catalog. */
  private Optional<AuditEntry> added(UserId u, IngestionResult result, String source) {
    if (!result.wasIndexed()) {
      return Optional.empty();
    }
    String detail =
        result.action().map(action -> source + " (duplicate: " + action + ")").orElse(source);
    return Optional.of(entry(u, AuditAction.FILE_ADDED).about(result.fileId()).saying(detail));
  }
}
