package co.omnistream.bootstrap.factory;

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
import java.util.List;

/**
 * Creates the adapters behind the driven ports, as one family (Abstract Factory, ADR 0010): the
 * local profile uses SQLite and the local disk, the server profile PostgreSQL. The composition root
 * asks for each port once, at start-up, and never knows which adapter it got.
 *
 * <p>The persistence ports come from the same adapter, so {@link #createUnitOfWork()} spans all of
 * them: a change and its audit entry are saved together (ADR 0025).
 */
public abstract class InfrastructureFactory {

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

  public abstract IFileStorage createStorage();

  /** The providers that recognize users, the local one first. */
  public abstract List<IIdentityProvider> createIdentityProviders();

  public abstract ICredentialRegistry createCredentialRegistry();

  public abstract IMetadataExtractor createMetadataExtractor();

  public abstract ITranscoder createTranscoder();

  public abstract IEventBus createEventBus();
}
