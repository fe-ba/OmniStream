package co.omnistream.bootstrap;

import co.omnistream.bootstrap.config.AppConfiguration;
import co.omnistream.bootstrap.factory.InfrastructureFactory;
import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.event.ICatalogObserver;
import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.port.dto.AuditQuery;
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
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * The adapters the bootstrap tests need, in memory: users, credentials, the unit of work, the event
 * bus and the audit log. Every other port answers with {@link UnsupportedOperationException}, since
 * building the application must not call them.
 */
final class InMemoryFactory extends InfrastructureFactory {

  InMemoryFactory() {
    super(demoConfiguration(), Clock.systemUTC());
  }

  /** The base factory keeps a configuration; these adapters never read it. */
  private static AppConfiguration demoConfiguration() {
    return new AppConfiguration(
        AppConfiguration.LOCAL,
        0,
        Path.of("library"),
        Path.of("omnistream.db"),
        Optional.empty(),
        Optional.empty(),
        Path.of("ffmpeg"),
        Path.of("exiftool"),
        false,
        5,
        Duration.ofHours(8));
  }

  final Users users = new Users();
  final Credentials credentials = new Credentials();
  final Bus bus = new Bus();
  final List<AuditEntry> audit = new CopyOnWriteArrayList<>();

  private final IUnitOfWork unitOfWork =
      new IUnitOfWork() {
        @Override
        public <T> T execute(Supplier<T> work) {
          return work.get();
        }
      };

  private final IAuditLog auditLog =
      new IAuditLog() {
        @Override
        public void append(AuditEntry e) {
          audit.add(e);
        }

        @Override
        public List<AuditEntry> find(AuditQuery q) {
          return audit.stream().filter(q::matches).limit(q.limit()).toList();
        }
      };

  @Override
  public IUserRepository createUserRepository() {
    return users;
  }

  @Override
  public ICredentialRegistry createCredentialRegistry() {
    return credentials;
  }

  @Override
  public IUnitOfWork createUnitOfWork() {
    return unitOfWork;
  }

  @Override
  public IEventBus createEventBus() {
    return bus;
  }

  @Override
  public IAuditLog createAuditLog() {
    return auditLog;
  }

  @Override
  public List<IIdentityProvider> createIdentityProviders() {
    return List.of(unused(IIdentityProvider.class));
  }

  @Override
  public ICatalogRepository createCatalogRepository() {
    return unused(ICatalogRepository.class);
  }

  @Override
  public ISessionRepository createSessionRepository() {
    return unused(ISessionRepository.class);
  }

  @Override
  public ISharedLinkRepository createSharedLinkRepository() {
    return unused(ISharedLinkRepository.class);
  }

  @Override
  public IChangeLog createChangeLog() {
    return unused(IChangeLog.class);
  }

  @Override
  public IOutboxRepository createOutboxRepository() {
    return unused(IOutboxRepository.class);
  }

  @Override
  public IDataBackup createDataBackup() {
    return unused(IDataBackup.class);
  }

  @Override
  public IConfigurationRepository createConfigurationRepository() {
    return unused(IConfigurationRepository.class);
  }

  @Override
  public ITokenRepository createTokenRepository() {
    return unused(ITokenRepository.class);
  }

  @Override
  public IFileStorage createFileStorage() {
    return unused(IFileStorage.class);
  }

  @Override
  public IMetadataExtractor createMetadataExtractor() {
    return unused(IMetadataExtractor.class);
  }

  @Override
  public ITranscoder createTranscoder() {
    return unused(ITranscoder.class);
  }

  /** A port the tests do not use: any call fails, so an unexpected one shows. */
  private static <T> T unused(Class<T> port) {
    return port.cast(
        Proxy.newProxyInstance(
            port.getClassLoader(),
            new Class<?>[] {port},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "toString" -> "unused " + port.getSimpleName();
                  case "hashCode" -> System.identityHashCode(proxy);
                  case "equals" -> proxy == args[0];
                  default ->
                      throw new UnsupportedOperationException(
                          port.getSimpleName() + "." + method.getName());
                }));
  }

  static final class Users implements IUserRepository {
    private final Map<UserId, User> byId = new LinkedHashMap<>();

    @Override
    public synchronized void save(User u) {
      byId.put(u.toSnapshot().id(), u);
    }

    @Override
    public synchronized Optional<User> findById(UserId id) {
      return Optional.ofNullable(byId.get(id));
    }

    @Override
    public synchronized Optional<User> findByUsername(String username) {
      return byId.values().stream()
          .filter(u -> u.toSnapshot().username().equalsIgnoreCase(username))
          .findFirst();
    }

    @Override
    public synchronized Optional<User> findByIdentity(LinkedIdentity i) {
      return byId.values().stream()
          .filter(u -> u.toSnapshot().identities().contains(i))
          .findFirst();
    }

    @Override
    public synchronized List<User> findAll() {
      return List.copyOf(byId.values());
    }
  }

  static final class Credentials implements ICredentialRegistry {
    final Map<String, String> passwords = new LinkedHashMap<>();

    @Override
    public synchronized LinkedIdentity register(String username, String password) {
      if (passwords.putIfAbsent(username, password) != null) {
        throw new IllegalStateException("Already registered: " + username);
      }
      return new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, username);
    }

    @Override
    public void changePassword(LinkedIdentity i, String newPassword) {
      throw new UnsupportedOperationException("changePassword");
    }
  }

  static final class Bus implements IEventBus {
    final List<ICatalogObserver> observers = new CopyOnWriteArrayList<>();
    final List<DomainEvent> published = new ArrayList<>();

    @Override
    public void publish(DomainEvent e) {
      published.add(e);
      observers.forEach(o -> o.onCatalogChanged(e));
    }

    @Override
    public void subscribe(ICatalogObserver observer) {
      observers.add(observer);
    }

    @Override
    public void unsubscribe(ICatalogObserver observer) {
      observers.remove(observer);
    }
  }
}
