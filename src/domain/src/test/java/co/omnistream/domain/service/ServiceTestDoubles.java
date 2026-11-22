package co.omnistream.domain.service;

import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.CatalogId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.sharing.SharedLink;
import co.omnistream.domain.port.dto.Credentials;
import co.omnistream.domain.port.dto.LocalCredentials;
import co.omnistream.domain.port.dto.SearchCriteria;
import co.omnistream.domain.port.dto.SessionToken;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.ICredentialRegistry;
import co.omnistream.domain.port.out.IIdentityProvider;
import co.omnistream.domain.port.out.ISharedLinkRepository;
import co.omnistream.domain.port.out.ITokenRepository;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
      CatalogSnapshot snapshot = c.toSnapshot();
      catalog =
          CatalogIndex.fromSnapshot(
              new CatalogSnapshot(
                  snapshot.id(),
                  snapshot.name(),
                  snapshot.version() + 1,
                  snapshot.files(),
                  snapshot.collections()));
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
}
