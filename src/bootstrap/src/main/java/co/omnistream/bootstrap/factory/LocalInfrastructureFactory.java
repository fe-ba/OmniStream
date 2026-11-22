package co.omnistream.bootstrap.factory;

import co.omnistream.bootstrap.config.AppConfiguration;
import co.omnistream.domain.port.out.IAuditLog;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.IChangeLog;
import co.omnistream.domain.port.out.IConfigurationRepository;
import co.omnistream.domain.port.out.ICredentialRegistry;
import co.omnistream.domain.port.out.IDataBackup;
import co.omnistream.domain.port.out.IEventBus;
import co.omnistream.domain.port.out.IFileStorage;
import co.omnistream.domain.port.out.IIdentityProvider;
import co.omnistream.domain.port.out.IMetadataExtractor;
import co.omnistream.domain.port.out.IOutboxRepository;
import co.omnistream.domain.port.out.ISessionRepository;
import co.omnistream.domain.port.out.ISharedLinkRepository;
import co.omnistream.domain.port.out.ITokenRepository;
import co.omnistream.domain.port.out.ITranscoder;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.infrastructure.database.Database;
import co.omnistream.infrastructure.identity.LocalIdentityAdapter;
import co.omnistream.infrastructure.persistence.SqlitePersistenceAdapter;
import java.time.Clock;
import java.util.List;

/**
 * Concrete factory of the local profile: a SQLite file next to the library and an account kept in
 * OmniStream itself (ADR 0010, ADR 0015).
 */
public final class LocalInfrastructureFactory extends InfrastructureFactory {

  private final CommonAdapters common;
  private final LocalIdentityAdapter identity;
  private final SqlitePersistenceAdapter persistence;

  public LocalInfrastructureFactory(AppConfiguration configuration, Clock clock) {
    super(configuration, clock);
    Database database = Database.sqlite(configuration.databasePath());
    this.identity = new LocalIdentityAdapter(database);
    this.persistence = new SqlitePersistenceAdapter(database);
    this.common = new CommonAdapters(configuration, clock);
  }

  @Override
  public ICatalogRepository createCatalogRepository() {
    return persistence;
  }

  @Override
  public ISessionRepository createSessionRepository() {
    return persistence;
  }

  @Override
  public IUserRepository createUserRepository() {
    return persistence;
  }

  @Override
  public ISharedLinkRepository createSharedLinkRepository() {
    return persistence;
  }

  @Override
  public IChangeLog createChangeLog() {
    return persistence;
  }

  @Override
  public IOutboxRepository createOutboxRepository() {
    return persistence;
  }

  @Override
  public IUnitOfWork createUnitOfWork() {
    return persistence;
  }

  @Override
  public IAuditLog createAuditLog() {
    return persistence;
  }

  @Override
  public IDataBackup createDataBackup() {
    return persistence;
  }

  @Override
  public IConfigurationRepository createConfigurationRepository() {
    return persistence;
  }

  @Override
  public ITokenRepository createTokenRepository() {
    return common.tokens();
  }

  @Override
  public IFileStorage createFileStorage() {
    return common.storage();
  }

  @Override
  public List<IIdentityProvider> createIdentityProviders() {
    return List.of(identity);
  }

  @Override
  public ICredentialRegistry createCredentialRegistry() {
    return identity;
  }

  @Override
  public IMetadataExtractor createMetadataExtractor() {
    return common.metadata();
  }

  @Override
  public ITranscoder createTranscoder() {
    return common.transcoder();
  }

  @Override
  public IEventBus createEventBus() {
    return common.eventBus();
  }

  @Override
  public void close() {
    persistence.database().close();
  }
}
