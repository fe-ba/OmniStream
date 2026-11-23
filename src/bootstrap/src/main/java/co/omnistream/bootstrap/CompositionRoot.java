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
import co.omnistream.domain.policy.TextFilePolicy;
import co.omnistream.domain.port.in.IAccessControl;
import co.omnistream.domain.port.in.IAuditTrail;
import co.omnistream.domain.port.in.ICatalogFeed;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.domain.port.in.IEventDispatch;
import co.omnistream.domain.port.in.IPlayback;
import co.omnistream.domain.port.in.ISearch;
import co.omnistream.domain.port.in.ISync;
import co.omnistream.domain.port.in.ITextEditing;
import co.omnistream.domain.port.out.IAuditLog;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.ICredentialRegistry;
import co.omnistream.domain.port.out.IEventBus;
import co.omnistream.domain.port.out.IFileStorage;
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
import co.omnistream.domain.service.TextEditingService;
import co.omnistream.domain.service.audit.AuditedAccessControl;
import co.omnistream.domain.service.audit.AuditedCatalogManagement;
import co.omnistream.domain.service.audit.AuditedSearch;
import co.omnistream.domain.service.audit.AuditedSync;
import co.omnistream.domain.service.audit.AuditedTextEditing;
import co.omnistream.domain.service.classification.DateRule;
import co.omnistream.domain.service.classification.MediaTypeRule;
import co.omnistream.domain.service.ingestion.ClassificationStep;
import co.omnistream.domain.service.ingestion.DuplicatesStep;
import co.omnistream.domain.service.ingestion.ExtractionStep;
import co.omnistream.domain.service.ingestion.IndexingStep;
import co.omnistream.domain.service.ingestion.IngestionStep;
import co.omnistream.domain.service.stream.DirectStream;
import co.omnistream.domain.service.stream.TranscodedStream;
import co.omnistream.presentation.cli.CliAdapter;
import co.omnistream.presentation.rest.RestApiAdapter;
import co.omnistream.presentation.rest.TextEditingRelay;
import co.omnistream.presentation.scheduler.BackupTask;
import co.omnistream.presentation.scheduler.ReindexTask;
import co.omnistream.presentation.scheduler.SchedulerAdapter;
import co.omnistream.presentation.scheduler.SyncTask;
import co.omnistream.presentation.web.WebUiAdapter;
import io.javalin.Javalin;
import java.io.PrintStream;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates every object of the application and connects them through their constructors: the only
 * place that knows the concrete classes (ADR 0013). Nothing else in OmniStream creates a service or
 * an adapter.
 *
 * <p>The order follows the dependencies: the adapters of the driven ports come from the factory of
 * the profile; the domain services receive them; the audit decorators wrap the services (ADR 0025);
 * the primary adapters receive the decorated ports. {@link #start()} then serves HTTP and runs the
 * scheduled tasks; {@link #runCommand} runs one command of the command line instead.
 *
 * <p>Both make sure that two accounts exist: the system account, which the command line and the
 * scheduler act as, and a first administrator, so that someone can log in the first time (ADR
 * 0029).
 */
public final class CompositionRoot implements AutoCloseable {

  /** How often pending events are published: how late a live change can arrive (ADR 0026). */
  static final Duration SYNC_EVERY = Duration.ofSeconds(2);

  /** How many pending events one run of the sync task publishes at most. */
  static final int SYNC_BATCH = 100;

  static final Duration REINDEX_EVERY = Duration.ofDays(1);
  static final Duration BACKUP_EVERY = Duration.ofDays(1);

  /** Username of the first administrator, created when nobody can administer the server. */
  static final String FIRST_ADMIN = "admin";

  /** Username of the account the server acts as; nobody can log in with it. */
  static final String SYSTEM_USERNAME = "omnistream-system";

  private static final LinkedIdentity SYSTEM_IDENTITY =
      new LinkedIdentity(LinkedIdentity.SYSTEM_ISSUER, "omnistream");

  /** Letters and digits that cannot be mistaken for each other when read from a screen. */
  private static final String PASSWORD_ALPHABET =
      "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";

  private static final Logger LOG = LoggerFactory.getLogger(CompositionRoot.class);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final AppConfiguration config;
  private final InfrastructureFactory factory;
  private final Clock clock;
  private final PrintStream console;

  // Driven ports, from the factory of the profile
  private final IUserRepository userRepository;
  private final ICredentialRegistry credentialRegistry;
  private final IUnitOfWork unitOfWork;
  private final IEventBus eventBus;

  // Driving ports, as the primary adapters see them: audited where they change something
  private final IAccessControl accessControl;
  private final ICatalogManagement catalogManagement;
  private final IPlayback playback;
  private final ISearch search;
  private final ISync sync;
  private final IAuditTrail auditTrail;
  private final ICatalogFeed catalogFeed;
  private final ITextEditing textEditing;
  private final IEventDispatch eventDispatch;
  private final SyncObserver syncObserver;

  private UserId systemIdentity;
  private Javalin server;
  private SchedulerAdapter scheduler;

  /** The application of {@code config}, built on the adapters of its profile. */
  public CompositionRoot(AppConfiguration config) {
    this(config, Clock.systemUTC());
  }

  /**
   * A composition root with the given clock and the factory of the configured profile; for tests.
   */
  CompositionRoot(AppConfiguration config, Clock clock) {
    this(config, selectFactory(config, clock), clock, System.out);
  }

  /**
   * The application of {@code config}, built on the adapters {@code factory} creates; tests pass
   * in-memory ones.
   *
   * @param console where the password of a new first administrator is shown, once
   */
  public CompositionRoot(
      AppConfiguration config, InfrastructureFactory factory, Clock clock, PrintStream console) {
    this.config = Objects.requireNonNull(config, "config");
    this.factory = Objects.requireNonNull(factory, "factory");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.console = Objects.requireNonNull(console, "console");

    // Driven ports
    userRepository = factory.createUserRepository();
    credentialRegistry = factory.createCredentialRegistry();
    unitOfWork = factory.createUnitOfWork();
    eventBus = factory.createEventBus();
    ICatalogRepository catalogRepository = factory.createCatalogRepository();
    IFileStorage storage = factory.createFileStorage();
    IAuditLog auditLog = factory.createAuditLog();
    var changeLog = factory.createChangeLog();
    var sessionRepository = factory.createSessionRepository();

    // Domain policies
    AccessPolicy accessPolicy = new AccessPolicy();
    DeduplicationPolicy deduplicationPolicy = new DeduplicationPolicy();

    // Domain services
    IAccessControl accessService =
        new AccessService(
            userRepository,
            accessPolicy,
            clock,
            factory.createIdentityProviders(),
            credentialRegistry,
            factory.createTokenRepository(),
            factory.createSharedLinkRepository(),
            catalogRepository,
            unitOfWork,
            config.tokenLifetime());
    ICatalogManagement catalogService =
        new CatalogService(
            userRepository,
            accessPolicy,
            clock,
            buildIngestionChain(storage, factory, deduplicationPolicy),
            catalogRepository,
            sessionRepository,
            storage,
            factory.createDataBackup(),
            factory.createConfigurationRepository(),
            deduplicationPolicy,
            unitOfWork);
    playback =
        new PlaybackService(
            userRepository,
            accessPolicy,
            clock,
            sessionRepository,
            catalogRepository,
            List.of(new DirectStream(storage), new TranscodedStream(factory.createTranscoder())));
    ISearch searchService =
        new SearchService(userRepository, accessPolicy, clock, catalogRepository, unitOfWork);
    ISync syncService =
        new SyncService(userRepository, accessPolicy, clock, changeLog, catalogRepository, storage);
    auditTrail = new AuditService(userRepository, accessPolicy, clock, auditLog);
    catalogFeed =
        new CatalogFeedService(userRepository, accessPolicy, clock, eventBus, catalogRepository);
    eventDispatch = new EventDispatchService(factory.createOutboxRepository(), eventBus);
    syncObserver = new SyncObserver(changeLog);
    ITextEditing textService =
        new TextEditingService(
            userRepository,
            accessPolicy,
            clock,
            catalogRepository,
            storage,
            unitOfWork,
            new TextFilePolicy());

    // Audit decorators around the ports that change something (ADR 0025)
    accessControl =
        new AuditedAccessControl(accessService, userRepository, auditLog, unitOfWork, clock);
    catalogManagement =
        new AuditedCatalogManagement(
            catalogService, catalogRepository, auditLog, unitOfWork, clock);
    search = new AuditedSearch(searchService, auditLog, unitOfWork, clock);
    sync = new AuditedSync(syncService, auditLog, unitOfWork, clock);
    textEditing =
        new AuditedTextEditing(textService, catalogRepository, auditLog, unitOfWork, clock);
  }

  /**
   * The factory of the profile (ADR 0010): {@link LocalInfrastructureFactory} for {@value
   * AppConfiguration#LOCAL} and {@link ServerInfrastructureFactory} for {@value
   * AppConfiguration#SERVER}. Tests inject a factory instead.
   */
  static InfrastructureFactory selectFactory(AppConfiguration config, Clock clock) {
    return switch (config.profile()) {
      case AppConfiguration.LOCAL -> new LocalInfrastructureFactory(config, clock);
      case AppConfiguration.SERVER -> new ServerInfrastructureFactory(config, clock);
      default -> throw new IllegalArgumentException("Unknown profile: " + config.profile());
    };
  }

  /**
   * Extraction, duplicates, classification and indexing, in that order (Chain of Responsibility).
   */
  private IngestionStep buildIngestionChain(
      IFileStorage storage, InfrastructureFactory factory, DeduplicationPolicy deduplication) {
    IngestionStep first = new ExtractionStep(storage, factory.createMetadataExtractor(), clock);
    first
        .chain(new DuplicatesStep(deduplication))
        .chain(new ClassificationStep(List.of(new MediaTypeRule(), new DateRule())))
        .chain(new IndexingStep());
    return first;
  }

  // ------------------------------------------------------------------ running

  /**
   * Serves the web UI and the REST API on the configured port, and starts the scheduled tasks.
   * Returns once the server is listening.
   */
  public synchronized void start() {
    if (server != null) {
      return;
    }
    UserId system = prepareAccounts();
    registerObservers();
    scheduler = createScheduler(system);
    scheduler.start();
    server = createHttpServer().start(config.httpPort());
    LOG.info("OmniStream ({} profile) is listening on port {}", config.profile(), server.port());
  }

  /** Runs one command of the command line, as the system account, and returns its exit code. */
  public int runCommand(List<String> args) {
    return new CliAdapter(catalogManagement, prepareAccounts()).execute(args);
  }

  /** The port the server listens on, once started; useful when the configuration asked for 0. */
  public synchronized int port() {
    if (server == null) {
      throw new IllegalStateException("The server has not started");
    }
    return server.port();
  }

  /** Stops serving and stops the scheduled tasks. Stopping again does nothing. */
  public synchronized void stop() {
    if (server != null) {
      server.stop();
      server = null;
    }
    if (scheduler != null) {
      scheduler.stop();
      scheduler = null;
      eventBus.unsubscribe(syncObserver);
    }
  }

  @Override
  public void close() {
    stop();
    factory.close();
  }

  /** The change log follows the catalog events while the server runs (Observer). */
  private void registerObservers() {
    eventBus.subscribe(syncObserver);
  }

  private Javalin createHttpServer() {
    RestApiAdapter api =
        new RestApiAdapter(
            catalogManagement, playback, search, accessControl, sync, auditTrail, catalogFeed);
    TextEditingRelay editor = new TextEditingRelay(textEditing, accessControl);
    WebUiAdapter web = new WebUiAdapter();
    return Javalin.create(javalin -> {
      api.registerRoutes(javalin);
      editor.registerRoutes(javalin);
      web.registerRoutes(javalin);
    });
  }

  private SchedulerAdapter createScheduler(UserId system) {
    SchedulerAdapter tasks = new SchedulerAdapter(system);
    tasks.register(SYNC_EVERY, new SyncTask(eventDispatch, SYNC_BATCH));
    tasks.register(REINDEX_EVERY, new ReindexTask(catalogManagement, config.libraryPath()));
    tasks.register(BACKUP_EVERY, new BackupTask(catalogManagement, backupFolder()));
    return tasks;
  }

  /** Next to the database: {@code ~/.omnistream/backups} with the default configuration. */
  private Path backupFolder() {
    return config.databasePath().toAbsolutePath().resolveSibling("backups");
  }

  // ------------------------------------------------------------------ accounts

  /** Makes sure the system account and a first administrator exist; returns the system account. */
  private synchronized UserId prepareAccounts() {
    if (systemIdentity == null) {
      systemIdentity = ensureSystemAccount();
      ensureFirstAdmin();
    }
    return systemIdentity;
  }

  /** The account the command line and the scheduler act as, created the first time. */
  private UserId ensureSystemAccount() {
    Optional<User> existing = userRepository.findByIdentity(SYSTEM_IDENTITY);
    if (existing.isPresent()) {
      return existing.get().toSnapshot().id();
    }
    User system = User.create(SYSTEM_USERNAME, Role.ADMIN, SYSTEM_IDENTITY);
    userRepository.save(system);
    LOG.info("Created the system account {}", SYSTEM_USERNAME);
    return system.toSnapshot().id();
  }

  /**
   * When no active administrator can log in (the first start), creates {@value #FIRST_ADMIN} with a
   * random password and shows it once on the console. It never goes to the log file, and it is not
   * kept anywhere else: whoever starts the server for the first time writes it down.
   */
  private void ensureFirstAdmin() {
    boolean someoneAdministers =
        userRepository.findAll().stream()
            .map(User::toSnapshot)
            .anyMatch(u ->
                u.active()
                    && u.role() == Role.ADMIN
                    && u.identities().stream()
                        .anyMatch(i -> !i.issuer().equals(LinkedIdentity.SYSTEM_ISSUER)));
    if (someoneAdministers) {
      return;
    }
    if (userRepository.findByUsername(FIRST_ADMIN).isPresent()) {
      LOG.warn(
          "Nobody can administer this server, and the username '{}' is taken: no administrator"
              + " was created",
          FIRST_ADMIN);
      return;
    }
    String password = randomPassword();
    unitOfWork.execute(() -> {
      LinkedIdentity identity = credentialRegistry.register(FIRST_ADMIN, password);
      userRepository.save(User.create(FIRST_ADMIN, Role.ADMIN, identity));
      return null;
    });
    LOG.info("Created the first administrator, '{}'; its password is on the console", FIRST_ADMIN);
    console.printf(
        "%n  OmniStream created the first administrator.%n"
            + "  Username: %s%n"
            + "  Password: %s%n"
            + "  It is shown only this once: write it down.%n%n",
        FIRST_ADMIN, password);
  }

  /** Twenty letters and digits in groups of five, about 115 bits of randomness. */
  static String randomPassword() {
    StringBuilder password = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      if (i > 0 && i % 5 == 0) {
        password.append('-');
      }
      password.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
    }
    return password.toString();
  }
}
