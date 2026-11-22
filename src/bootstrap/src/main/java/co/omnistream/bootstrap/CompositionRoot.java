package co.omnistream.bootstrap;

import co.omnistream.bootstrap.config.AppConfiguration;
import co.omnistream.bootstrap.factory.InfrastructureFactory;
import co.omnistream.bootstrap.factory.LocalInfrastructureFactory;
import co.omnistream.bootstrap.factory.ServerInfrastructureFactory;
import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.policy.DeduplicationPolicy;
import co.omnistream.domain.port.in.IAccessControl;
import co.omnistream.domain.port.in.ICatalogFeed;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.domain.port.in.ISearch;
import co.omnistream.domain.port.in.ISync;
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
import co.omnistream.domain.service.AccessService;
import co.omnistream.domain.service.AuditService;
import co.omnistream.domain.service.CatalogFeedService;
import co.omnistream.domain.service.CatalogService;
import co.omnistream.domain.service.EventDispatchService;
import co.omnistream.domain.service.PlaybackService;
import co.omnistream.domain.service.SearchService;
import co.omnistream.domain.service.SyncObserver;
import co.omnistream.domain.service.SyncService;
import co.omnistream.domain.service.audit.AuditedAccessControl;
import co.omnistream.domain.service.audit.AuditedCatalogManagement;
import co.omnistream.domain.service.audit.AuditedSearch;
import co.omnistream.domain.service.audit.AuditedSync;
import co.omnistream.domain.service.classification.DateRule;
import co.omnistream.domain.service.classification.MediaTypeRule;
import co.omnistream.domain.service.ingestion.ClassificationStep;
import co.omnistream.domain.service.ingestion.DuplicatesStep;
import co.omnistream.domain.service.ingestion.ExtractionStep;
import co.omnistream.domain.service.ingestion.IndexingStep;
import co.omnistream.domain.service.ingestion.IngestionStep;
import co.omnistream.domain.service.stream.DirectStream;
import co.omnistream.domain.service.stream.TranscodedStream;
import co.omnistream.infrastructure.media.CachedTranscoder;
import co.omnistream.infrastructure.persistence.CachedCatalogRepository;
import co.omnistream.presentation.cli.CliAdapter;
import co.omnistream.presentation.rest.RestApiAdapter;
import co.omnistream.presentation.scheduler.BackupTask;
import co.omnistream.presentation.scheduler.ReindexTask;
import co.omnistream.presentation.scheduler.SchedulerAdapter;
import co.omnistream.presentation.scheduler.SyncTask;
import co.omnistream.presentation.web.WebUiAdapter;
import io.javalin.Javalin;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Composition root: it is the only place that knows every adapter, creates the domain services and
 * connects the driving ports to the primary adapters (ADR 0013).
 *
 * <p>It reads the {@link AppConfiguration}, picks the infrastructure factory of the profile,
 * decorates the ports the configuration asks for, registers the observer of the change log, builds
 * the ingestion chain and starts the HTTP server and the scheduler. Nothing here decides business
 * rules: it only wires.
 */
public final class CompositionRoot implements AutoCloseable {

  /** How long the catalog repository keeps the aggregate in memory. */
  private static final Duration CATALOG_TTL = Duration.ofSeconds(30);

  /** How much space the transcoding cache may use. */
  private static final long TRANSCODE_CACHE_BYTES = 5L * 1024 * 1024 * 1024;

  private final AppConfiguration configuration;
  private final Clock clock;
  private final InfrastructureFactory factory;

  private Javalin server;
  private SchedulerAdapter scheduler;
  private CliAdapter cli;
  private boolean started;

  public CompositionRoot(AppConfiguration configuration) {
    this(configuration, Clock.systemUTC());
  }

  /** A composition root with the given clock; for tests. */
  CompositionRoot(AppConfiguration configuration, Clock clock) {
    this.configuration = Objects.requireNonNull(configuration, "configuration");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.factory = selectFactory(configuration, clock);
  }

  /**
   * Creates every adapter and service, registers the observers and starts the HTTP server and the
   * scheduler. Calling it again does nothing.
   */
  public synchronized void start() {
    if (started) {
      return;
    }
    ICatalogRepository catalogRepository = decorateCatalog(factory.createCatalogRepository());
    IUserRepository userRepository = factory.createUserRepository();
    ISessionRepository sessionRepository = factory.createSessionRepository();
    ISharedLinkRepository sharedLinkRepository = factory.createSharedLinkRepository();
    IChangeLog changeLog = factory.createChangeLog();
    IOutboxRepository outbox = factory.createOutboxRepository();
    IUnitOfWork unitOfWork = factory.createUnitOfWork();
    IAuditLog auditLog = factory.createAuditLog();
    IDataBackup dataBackup = factory.createDataBackup();
    IConfigurationRepository configurationRepository = factory.createConfigurationRepository();
    ITokenRepository tokens = factory.createTokenRepository();
    IFileStorage storage = factory.createFileStorage();
    IMetadataExtractor metadata = factory.createMetadataExtractor();
    ITranscoder transcoder = decorateTranscoder(factory.createTranscoder());
    IEventBus eventBus = factory.createEventBus();
    List<IIdentityProvider> providers = factory.createIdentityProviders();
    ICredentialRegistry credentials = factory.createCredentialRegistry();

    registerObservers(eventBus, changeLog);

    AccessPolicy accessPolicy = new AccessPolicy();
    IngestionStep ingestion = buildIngestionChain(storage, metadata);

    AccessService accessService =
        new AccessService(
            userRepository,
            accessPolicy,
            clock,
            providers,
            credentials,
            tokens,
            sharedLinkRepository,
            catalogRepository,
            unitOfWork);
    CatalogService catalogService =
        new CatalogService(
            userRepository,
            accessPolicy,
            clock,
            ingestion,
            catalogRepository,
            sessionRepository,
            storage,
            dataBackup,
            configurationRepository,
            new DeduplicationPolicy());
    SearchService searchService =
        new SearchService(userRepository, accessPolicy, clock, catalogRepository);
    PlaybackService playbackService =
        new PlaybackService(
            userRepository,
            accessPolicy,
            clock,
            sessionRepository,
            catalogRepository,
            List.of(new DirectStream(storage), new TranscodedStream(transcoder)));
    SyncService syncService =
        new SyncService(userRepository, accessPolicy, clock, changeLog, catalogRepository, storage);
    AuditService auditService = new AuditService(userRepository, accessPolicy, clock, auditLog);
    EventDispatchService eventDispatch = new EventDispatchService(outbox, eventBus);
    ICatalogFeed catalogFeed =
        new CatalogFeedService(userRepository, accessPolicy, clock, eventBus, catalogRepository);

    ICatalogManagement catalogManagement =
        new AuditedCatalogManagement(
            catalogService, catalogRepository, auditLog, unitOfWork, clock);
    ISearch search = new AuditedSearch(searchService, auditLog, unitOfWork, clock);
    ISync sync = new AuditedSync(syncService, auditLog, unitOfWork, clock);
    IAccessControl access =
        new AuditedAccessControl(accessService, userRepository, auditLog, unitOfWork, clock);

    UserId systemIdentity = systemIdentity(userRepository);
    RestApiAdapter restApi =
        new RestApiAdapter(
            catalogManagement, playbackService, search, access, sync, auditService, catalogFeed);
    WebUiAdapter webUi = new WebUiAdapter();
    // The CLI of the desktop app shares the same ports; a console entry point can use it
    cli = new CliAdapter(catalogManagement, systemIdentity);

    scheduler = new SchedulerAdapter(systemIdentity);
    scheduler.register(Duration.ofSeconds(2), new SyncTask(eventDispatch, 100));
    scheduler.register(
        Duration.ofHours(6), new ReindexTask(catalogManagement, configuration.libraryPath()));
    scheduler.register(Duration.ofDays(1), new BackupTask(catalogManagement, backupFolder()));

    server =
        Javalin.create(config -> {
          restApi.registerRoutes(config);
          webUi.registerRoutes(config);
        });
    server.start(configuration.httpPort());
    scheduler.start();
    started = true;
  }

  /** Stops the server and the scheduled tasks, and releases the factory. */
  public synchronized void close() {
    if (scheduler != null) {
      scheduler.stop();
      scheduler = null;
    }
    if (server != null) {
      server.stop();
      server = null;
    }
    factory.close();
    started = false;
  }

  /** The HTTP server, once started; for the tests that need its port. */
  public synchronized Javalin server() {
    return server;
  }

  /** The console adapter, once started; {@code null} before. */
  public synchronized CliAdapter cli() {
    return cli;
  }

  private ICatalogRepository decorateCatalog(ICatalogRepository repository) {
    if (!configuration.cacheEnabled()) {
      return repository;
    }
    return new CachedCatalogRepository(repository, CATALOG_TTL, clock);
  }

  private ITranscoder decorateTranscoder(ITranscoder transcoder) {
    if (!configuration.cacheEnabled()) {
      return transcoder;
    }
    Path folder = Path.of(System.getProperty("java.io.tmpdir"), "omnistream", "media-cache");
    return new CachedTranscoder(transcoder, folder, TRANSCODE_CACHE_BYTES);
  }

  private IngestionStep buildIngestionChain(IFileStorage storage, IMetadataExtractor metadata) {
    return new ExtractionStep(storage, metadata, clock)
        .chain(new DuplicatesStep(new DeduplicationPolicy()))
        .chain(new ClassificationStep(List.of(new MediaTypeRule(), new DateRule())))
        .chain(new IndexingStep());
  }

  private void registerObservers(IEventBus eventBus, IChangeLog changeLog) {
    eventBus.subscribe(new SyncObserver(changeLog));
  }

  /** The administrator the CLI and the scheduled tasks act as; it is created once. */
  private static UserId systemIdentity(IUserRepository users) {
    LinkedIdentity identity = new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, "system");
    return users
        .findByIdentity(identity)
        .map(user -> user.toSnapshot().id())
        .orElseGet(() -> {
          User system = User.create("system", Role.ADMIN, identity);
          users.save(system);
          return system.toSnapshot().id();
        });
  }

  private Path backupFolder() {
    Path parent = configuration.databasePath().toAbsolutePath().getParent();
    return (parent == null ? Path.of(".") : parent).resolve("backups");
  }

  private static InfrastructureFactory selectFactory(AppConfiguration configuration, Clock clock) {
    return switch (configuration.profile()) {
      case AppConfiguration.LOCAL -> new LocalInfrastructureFactory(configuration, clock);
      case AppConfiguration.SERVER -> new ServerInfrastructureFactory(configuration, clock);
      default -> throw new IllegalArgumentException("Unknown profile: " + configuration.profile());
    };
  }
}
