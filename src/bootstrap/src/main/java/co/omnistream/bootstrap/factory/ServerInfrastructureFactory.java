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
import co.omnistream.infrastructure.persistence.PostgresPersistenceAdapter;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Concrete factory of the server profile: a PostgreSQL database shared by every client (ADR 0015).
 *
 * <p>In version 1.0 the library is still a local folder and the accounts are local, as the diagram
 * says: the network storage and OIDC adapters belong to version 2.0 (ADR 0009). The PostgreSQL
 * credentials come from {@code OMNISTREAM_DATABASE_USER} and {@code OMNISTREAM_DATABASE_PASSWORD},
 * because the JDBC URL must not carry a password.
 */
public final class ServerInfrastructureFactory extends InfrastructureFactory {

  private final CommonAdapters common;
  private final LocalIdentityAdapter identity;
  private final PostgresPersistenceAdapter persistence;

  public ServerInfrastructureFactory(AppConfiguration configuration, Clock clock) {
    super(configuration, clock);
    Database database =
        Database.postgres(
            configuration
                .databaseUrl()
                .orElseThrow(() ->
                    new IllegalStateException(
                        "The server profile needs database.url (OMNISTREAM_DATABASE_URL)")),
            environment("OMNISTREAM_DATABASE_USER"),
            environment("OMNISTREAM_DATABASE_PASSWORD"));
    this.identity = new LocalIdentityAdapter(database);
    this.persistence = new PostgresPersistenceAdapter(database);
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

  /** Local accounts in version 1.0; an OIDC provider is added here in version 2.0. */
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

  private static Optional<String> environment(String name) {
    return Optional.ofNullable(System.getenv(name)).map(String::strip).filter(v -> !v.isEmpty());
  }
}
