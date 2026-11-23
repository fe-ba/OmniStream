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
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Abstract factory of the secondary adapters (Abstract Factory, ADR 0013).
 *
 * <p>The composition root asks it for every port and never names a concrete adapter, so the local
 * and the server profiles are chosen once, at startup, from the configuration. A factory hands out
 * the same instance of each adapter every time: the persistence adapter, for example, implements
 * ten ports at once and must be shared.
 */
public abstract class InfrastructureFactory implements AutoCloseable {

  protected final AppConfiguration configuration;
  protected final Clock clock;

  protected InfrastructureFactory(AppConfiguration configuration, Clock clock) {
    this.configuration = Objects.requireNonNull(configuration, "configuration");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** The JDBC adapter that implements every persistence port. */
  public abstract ICatalogRepository createCatalogRepository();

  public abstract ISessionRepository createSessionRepository();

  public abstract IUserRepository createUserRepository();

  public abstract ISharedLinkRepository createSharedLinkRepository();

  public abstract IChangeLog createChangeLog();

  public abstract IOutboxRepository createOutboxRepository();

  public abstract IUnitOfWork createUnitOfWork();

  public abstract IAuditLog createAuditLog();

  public abstract IDataBackup createDataBackup();

  public abstract IConfigurationRepository createConfigurationRepository();

  public abstract ITokenRepository createTokenRepository();

  public abstract IFileStorage createFileStorage();

  /** The identity providers to try, in order (Strategy). */
  public abstract List<IIdentityProvider> createIdentityProviders();

  public abstract ICredentialRegistry createCredentialRegistry();

  public abstract IMetadataExtractor createMetadataExtractor();

  public abstract ITranscoder createTranscoder();

  public abstract IEventBus createEventBus();

  /** Nothing is kept open between calls, so closing a factory only releases what it owns. */
  @Override
  public void close() {}
}
