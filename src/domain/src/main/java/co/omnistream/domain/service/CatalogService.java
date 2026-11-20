package co.omnistream.domain.service;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.IngestionResult;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.policy.DeduplicationPolicy;
import co.omnistream.domain.port.dto.ServerStatus;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.IConfigurationRepository;
import co.omnistream.domain.port.out.IDataBackup;
import co.omnistream.domain.port.out.IFileStorage;
import co.omnistream.domain.port.out.ISessionRepository;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.service.ingestion.IngestionContext;
import co.omnistream.domain.service.ingestion.IngestionStep;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/** Domain service that implements the ICatalogManagement driving port. */
public final class CatalogService extends AuthorizedService implements ICatalogManagement {
  private final IngestionStep ingestionChain;
  private final ICatalogRepository catalogRepository;
  private final ISessionRepository sessionRepository;
  private final IFileStorage storage;
  private final IDataBackup dataBackup;
  private final IConfigurationRepository configurationRepository;
  private final DeduplicationPolicy deduplicationPolicy;

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
      DeduplicationPolicy deduplicationPolicy) {
    super(userRepository, accessPolicy, clock);
    this.ingestionChain = ingestionChain;
    this.catalogRepository = catalogRepository;
    this.sessionRepository = sessionRepository;
    this.storage = storage;
    this.dataBackup = dataBackup;
    this.configurationRepository = configurationRepository;
    this.deduplicationPolicy = deduplicationPolicy;
  }

  @Override
  public IngestionResult ingestFile(UserId u, Path path) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public IngestionResult uploadFile(UserId u, String fileName, InputStream data) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public int reindexLibrary(UserId u, Path root) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public List<MediaFileSnapshot> detectDuplicates(UserId u) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void editMetadata(UserId u, ItemId id, String key, String value) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void deleteFile(UserId u, ItemId id) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Path backupCatalog(UserId admin, Path destination) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void configureLibraryPath(UserId admin, Path path) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public ServerStatus monitorServerStatus(UserId admin) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  private IngestionResult ingest(IngestionContext ctx) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
