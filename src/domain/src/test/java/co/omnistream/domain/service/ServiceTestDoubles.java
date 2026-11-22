package co.omnistream.domain.service;

import co.omnistream.domain.event.CatalogEvent;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.exception.VersionConflict;
import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.CatalogId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.model.playback.PlaybackSession;
import co.omnistream.domain.model.playback.SessionId;
import co.omnistream.domain.model.playback.SessionStatus;
import co.omnistream.domain.model.sharing.SharedLink;
import co.omnistream.domain.port.dto.Credentials;
import co.omnistream.domain.port.dto.LocalCredentials;
import co.omnistream.domain.port.dto.SearchCriteria;
import co.omnistream.domain.port.dto.SessionToken;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.IConfigurationRepository;
import co.omnistream.domain.port.out.ICredentialRegistry;
import co.omnistream.domain.port.out.IDataBackup;
import co.omnistream.domain.port.out.IFileStorage;
import co.omnistream.domain.port.out.IIdentityProvider;
import co.omnistream.domain.port.out.IMetadataExtractor;
import co.omnistream.domain.port.out.ISessionRepository;
import co.omnistream.domain.port.out.ISharedLinkRepository;
import co.omnistream.domain.port.out.ITokenRepository;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import co.omnistream.domain.snapshot.PlaybackSessionSnapshot;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Local in-memory port doubles for domain service unit tests while issues #6 and #7 are in flight
 * (removed once {@code co.omnistream.domain.port.out.memory} lands).
 */
final class ServiceTestDoubles {

  static final Instant NOW = Instant.parse("2026-11-21T18:00:00Z");

  private ServiceTestDoubles() {}

  static final class InMemoryUsers implements IUserRepository {
    private final Map<UserId, User> users = new LinkedHashMap<>();

    @Override
    public void save(User u) {
      users.put(u.toSnapshot().id(), User.fromSnapshot(u.toSnapshot()));
    }

    @Override
    public Optional<User> findById(UserId id) {
      return Optional.ofNullable(users.get(id)).map(u -> User.fromSnapshot(u.toSnapshot()));
    }

    @Override
    public Optional<User> findByUsername(String username) {
      return users.values().stream()
          .filter(user -> user.toSnapshot().username().equals(username))
          .findFirst()
          .map(u -> User.fromSnapshot(u.toSnapshot()));
    }

    @Override
    public Optional<User> findByIdentity(LinkedIdentity i) {
      return users.values().stream()
          .filter(user -> user.toSnapshot().identities().contains(i))
          .findFirst()
          .map(u -> User.fromSnapshot(u.toSnapshot()));
    }

    @Override
    public List<User> findAll() {
      return users.values().stream().map(u -> User.fromSnapshot(u.toSnapshot())).toList();
    }
  }

  static final class InMemoryTokens implements ITokenRepository {
    private final Map<String, SessionToken> tokens = new HashMap<>();

    @Override
    public void save(SessionToken t) {
      tokens.put(t.value(), t);
    }

    @Override
    public Optional<SessionToken> find(String value) {
      return Optional.ofNullable(tokens.get(value));
    }

    @Override
    public void delete(String value) {
      tokens.remove(value);
    }
  }

  static final class InMemorySharedLinks implements ISharedLinkRepository {
    private final Map<String, SharedLink> links = new HashMap<>();

    @Override
    public void save(SharedLink e) {
      links.put(e.toSnapshot().token(), SharedLink.fromSnapshot(e.toSnapshot()));
    }

    @Override
    public Optional<SharedLink> findByToken(String token) {
      return Optional.ofNullable(links.get(token))
          .map(link -> SharedLink.fromSnapshot(link.toSnapshot()));
    }
  }

  static final class InMemoryCredentials implements IIdentityProvider, ICredentialRegistry {
    private final Map<String, String> passwords = new HashMap<>();

    @Override
    public LinkedIdentity register(String username, String password) {
      passwords.put(username, password);
      return new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, username);
    }

    @Override
    public void changePassword(LinkedIdentity i, String newPassword) {
      passwords.put(i.subject(), newPassword);
    }

    @Override
    public boolean supports(Credentials c) {
      return c instanceof LocalCredentials && LinkedIdentity.LOCAL_ISSUER.equals(c.provider());
    }

    @Override
    public Optional<LinkedIdentity> verify(Credentials c) {
      if (!(c instanceof LocalCredentials local)) {
        return Optional.empty();
      }
      String stored = passwords.get(local.username());
      if (stored != null && stored.equals(local.password())) {
        return Optional.of(new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, local.username()));
      }
      return Optional.empty();
    }
  }

  static final class TrackingUnitOfWork implements IUnitOfWork {
    int executions;
    private int depth;

    @Override
    public <T> T execute(Supplier<T> work) {
      executions++;
      depth++;
      try {
        return work.get();
      } finally {
        depth--;
      }
    }

    boolean inside() {
      return depth > 0;
    }
  }

  static final class InMemoryCatalog implements ICatalogRepository {
    final List<CatalogEvent> outbox = new ArrayList<>();
    private CatalogIndex catalog =
        CatalogIndex.fromSnapshot(
            new CatalogSnapshot(
                new CatalogId(UUID.randomUUID()), "OmniStream", 0L, List.of(), List.of()));

    @Override
    public CatalogIndex getCatalog() {
      return CatalogIndex.fromSnapshot(catalog.toSnapshot());
    }

    @Override
    public void save(CatalogIndex c) {
      CatalogSnapshot incoming = c.toSnapshot();
      long currentVersion = catalog.toSnapshot().version();
      if (incoming.version() != currentVersion) {
        throw new VersionConflict(
            "Expected catalog version " + currentVersion + " but got " + incoming.version());
      }
      outbox.addAll(c.pullEvents());
      catalog =
          CatalogIndex.fromSnapshot(
              new CatalogSnapshot(
                  incoming.id(),
                  incoming.name(),
                  incoming.version() + 1,
                  incoming.files(),
                  incoming.collections()));
    }

    @Override
    public Optional<MediaFileSnapshot> findFile(ItemId id) {
      return catalog.toSnapshot().files().stream().filter(f -> f.id().equals(id)).findFirst();
    }

    @Override
    public Optional<MediaFileSnapshot> findByFingerprint(Fingerprint h) {
      return catalog.toSnapshot().files().stream()
          .filter(f -> f.fingerprint().matches(h))
          .findFirst();
    }

    @Override
    public List<MediaFileSnapshot> findByText(String txt) {
      return List.of();
    }

    @Override
    public List<MediaFileSnapshot> findByCriteria(SearchCriteria c) {
      return List.of();
    }
  }

  static final class InMemoryFileStorage implements IFileStorage {
    private final Map<Path, byte[]> files = new LinkedHashMap<>();
    long freeSpaceBytes = 10_000_000L;

    void put(Path path, byte[] content) {
      files.put(path.normalize(), content.clone());
    }

    @Override
    public InputStream read(Path path) {
      byte[] bytes = files.get(path.normalize());
      if (bytes == null) {
        throw new ResourceNotFound("File not found in storage: " + path);
      }
      return new ByteArrayInputStream(bytes);
    }

    @Override
    public void write(Path path, InputStream data) {
      try {
        files.put(path.normalize(), data.readAllBytes());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public void delete(Path path) {
      files.remove(path.normalize());
    }

    @Override
    public boolean exists(Path path) {
      return files.containsKey(path.normalize());
    }

    @Override
    public List<Path> list(Path root) {
      Path normalizedRoot = root.normalize();
      return files.keySet().stream()
          .filter(path -> path.startsWith(normalizedRoot))
          .sorted()
          .toList();
    }

    @Override
    public Fingerprint computeFingerprint(Path path) {
      byte[] bytes = files.get(path.normalize());
      if (bytes == null) {
        throw new ResourceNotFound("File not found in storage: " + path);
      }
      try {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        return new Fingerprint("SHA-256", HexFormat.of().formatHex(digest));
      } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public Path copy(Path source, Path destination) {
      byte[] bytes = files.get(source.normalize());
      if (bytes == null) {
        throw new ResourceNotFound("File not found in storage: " + source);
      }
      files.put(destination.normalize(), bytes.clone());
      return destination;
    }

    @Override
    public long freeSpace() {
      return freeSpaceBytes;
    }
  }

  static final class StubMetadataExtractor implements IMetadataExtractor {
    private final Map<Path, List<MetadataEntry>> metadataByPath = new HashMap<>();
    Set<MediaType> unsupportedTypes = Set.of();

    void put(Path path, List<MetadataEntry> entries) {
      metadataByPath.put(path.normalize(), List.copyOf(entries));
    }

    @Override
    public boolean supports(MediaType type) {
      return !unsupportedTypes.contains(type);
    }

    @Override
    public List<MetadataEntry> extract(Path path) {
      return metadataByPath.getOrDefault(path.normalize(), List.of());
    }
  }

  static final class InMemorySessions implements ISessionRepository {
    private final Map<SessionId, PlaybackSession> sessions = new LinkedHashMap<>();

    @Override
    public void save(PlaybackSession s) {
      PlaybackSessionSnapshot snapshot = s.toSnapshot();
      sessions.put(snapshot.id(), PlaybackSession.fromSnapshot(snapshot));
    }

    @Override
    public Optional<PlaybackSession> findById(SessionId id) {
      return Optional.ofNullable(sessions.get(id))
          .map(session -> PlaybackSession.fromSnapshot(session.toSnapshot()));
    }

    @Override
    public Optional<PlaybackSession> findLastSession(UserId u, ItemId a) {
      return sessions.values().stream()
          .filter(session -> {
            PlaybackSessionSnapshot s = session.toSnapshot();
            return s.userId().equals(u) && s.fileId().equals(a);
          })
          .max(Comparator.comparing(session -> session.toSnapshot().startedAt()))
          .map(session -> PlaybackSession.fromSnapshot(session.toSnapshot()));
    }

    @Override
    public int countActive() {
      return (int)
          sessions.values().stream()
              .filter(session -> {
                SessionStatus status = session.toSnapshot().status();
                return status == SessionStatus.ACTIVE || status == SessionStatus.PAUSED;
              })
              .count();
    }
  }

  static final class InMemoryConfiguration implements IConfigurationRepository {
    private Path libraryPath = Path.of("/library");

    @Override
    public Path getLibraryPath() {
      return libraryPath;
    }

    @Override
    public void saveLibraryPath(Path path) {
      this.libraryPath = path;
    }
  }

  static final class StubDataBackup implements IDataBackup {
    Path lastDestination;

    @Override
    public Path backup(Path destination) {
      this.lastDestination = destination;
      return destination.resolve("omnistream-backup.db");
    }
  }
}
