package co.omnistream.presentation.demo;

import co.omnistream.domain.event.CatalogEvent;
import co.omnistream.domain.event.CollectionCreated;
import co.omnistream.domain.event.CollectionModified;
import co.omnistream.domain.event.FileDeleted;
import co.omnistream.domain.event.FileIngested;
import co.omnistream.domain.event.FileModified;
import co.omnistream.domain.event.ICatalogObserver;
import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.InvalidCredentials;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.model.catalog.CatalogId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.DuplicateAction;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.IndexingStatus;
import co.omnistream.domain.model.catalog.IngestionResult;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.model.catalog.MetadataSource;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.model.playback.PlaybackSession;
import co.omnistream.domain.model.playback.SessionId;
import co.omnistream.domain.model.playback.SessionStatus;
import co.omnistream.domain.model.sharing.SharedLinkId;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.dto.AccessGrant;
import co.omnistream.domain.port.dto.AuditQuery;
import co.omnistream.domain.port.dto.CollectionCommand;
import co.omnistream.domain.port.dto.Credentials;
import co.omnistream.domain.port.dto.LocalCredentials;
import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.OfflinePackage;
import co.omnistream.domain.port.dto.SearchCriteria;
import co.omnistream.domain.port.dto.ServerStatus;
import co.omnistream.domain.port.dto.SessionToken;
import co.omnistream.domain.port.dto.StreamProfile;
import co.omnistream.domain.port.dto.SyncDelta;
import co.omnistream.domain.port.in.IAccessControl;
import co.omnistream.domain.port.in.IAuditTrail;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.domain.port.in.IPlayback;
import co.omnistream.domain.port.in.ISearch;
import co.omnistream.domain.port.in.ISync;
import co.omnistream.domain.port.out.IAuditLog;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.IEventSubscriber;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.snapshot.CatalogItemSnapshot;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import co.omnistream.domain.snapshot.PlaybackSessionSnapshot;
import co.omnistream.domain.snapshot.SharedLinkSnapshot;
import co.omnistream.domain.snapshot.UserSnapshot;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

/**
 * Driving ports in memory, with a few sample files and people, so the REST API and the web UI can
 * be tried in a browser before the services and the infrastructure exist. It is a test double, not
 * the application: it keeps everything in memory. Who may see and do what is decided by the real
 * {@link AccessPolicy}, counting collections, and delegation follows ADR 0024; {@link DemoServer}
 * wraps it in the real audit decorators and the real live feed.
 *
 * <p>Accounts (password = username): {@code admin}, the administrator; {@code ana}, who manages the
 * "Tones" collection; {@code luis}, who can only watch and listen to it. In the demo, whatever a
 * user uploads or creates, that user manages.
 */
final class DemoLibrary
    implements IAccessControl, ICatalogManagement, ISearch, IPlayback, ISync, IAuditTrail {

  static final String USERNAME = "admin";
  static final String PASSWORD = "admin";

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final CatalogId CATALOG = new CatalogId(UUID.randomUUID());

  private final AccessPolicy policy = new AccessPolicy();
  private final Map<UserId, User> users = new LinkedHashMap<>();
  private final Map<String, String> passwords = new HashMap<>();
  private final Map<String, SessionToken> tokens = new HashMap<>();
  private final Map<String, SharedLinkSnapshot> links = new HashMap<>();
  private final Map<ItemId, MediaFileSnapshot> files = new LinkedHashMap<>();
  private final Map<ItemId, byte[]> contents = new HashMap<>();
  private final Map<ItemId, CollectionSnapshot> collections = new LinkedHashMap<>();
  private final Map<SessionId, PlaybackSession> sessions = new HashMap<>();
  private final List<AuditEntry> trail = new ArrayList<>();
  private final List<ICatalogObserver> observers = new CopyOnWriteArrayList<>();
  private final UserId admin;

  DemoLibrary() {
    admin = account(USERNAME, Role.ADMIN);
    UserId ana = account("ana", Role.USER);
    UserId luis = account("luis", Role.USER);
    ItemId low = add("A 440 Hz tone.wav", tone(440, 8), Duration.ofSeconds(8));
    ItemId high = add("A 660 Hz tone.wav", tone(660, 5), Duration.ofSeconds(5));
    ItemId picture = add("Gradient.png", gradient(), Duration.ZERO);
    ItemId tones = new ItemId(UUID.randomUUID());
    collections.put(
        tones, new CollectionSnapshot(tones, "Tones", "Sample sounds", List.of(low, high)));
    ItemId pictures = new ItemId(UUID.randomUUID());
    collections.put(pictures, new CollectionSnapshot(pictures, "Pictures", "", List.of(picture)));
    users.get(ana).grant(new Permission(tones, PermissionLevel.MANAGE));
    users.get(luis).grant(new Permission(tones, PermissionLevel.READ));

    // A little history, so the audit screens are not empty on the first visit
    Instant now = Instant.now();
    trail.add(
        seed(admin, AuditAction.USER_CREATED, now.minus(Duration.ofDays(3)))
            .concerning(ana)
            .saying("ana as USER"));
    trail.add(
        seed(admin, AuditAction.USER_CREATED, now.minus(Duration.ofDays(3)))
            .concerning(luis)
            .saying("luis as USER"));
    trail.add(
        seed(admin, AuditAction.COLLECTION_CREATED, now.minus(Duration.ofDays(2)))
            .about(tones)
            .saying("Tones"));
    trail.add(
        seed(admin, AuditAction.PERMISSION_GRANTED, now.minus(Duration.ofDays(2)))
            .about(tones)
            .concerning(ana)
            .saying("MANAGE"));
    trail.add(
        seed(ana, AuditAction.PERMISSION_GRANTED, now.minus(Duration.ofHours(20)))
            .about(tones)
            .concerning(luis)
            .saying("READ"));
    trail.add(
        seed(ana, AuditAction.FILE_TAGGED, now.minus(Duration.ofHours(5)))
            .about(low)
            .saying("calm"));
    trail.add(seed(luis, AuditAction.LOGIN_FAILED, now.minus(Duration.ofHours(2))).saying("local"));
  }

  // ------------------------------------------------------------------ ports for the real domain

  /** The users, as the audit decorators and the live feed read them. */
  IUserRepository userRepository() {
    DemoLibrary library = this;
    return new IUserRepository() {
      @Override
      public void save(User u) {
        synchronized (library) {
          users.put(u.toSnapshot().id(), u);
        }
      }

      @Override
      public Optional<User> findById(UserId id) {
        synchronized (library) {
          return Optional.ofNullable(users.get(id));
        }
      }

      @Override
      public Optional<User> findByUsername(String username) {
        synchronized (library) {
          return users.values().stream()
              .filter(u -> u.toSnapshot().username().equals(username))
              .findFirst();
        }
      }

      @Override
      public Optional<User> findByIdentity(LinkedIdentity i) {
        synchronized (library) {
          return users.values().stream()
              .filter(u -> u.toSnapshot().identities().contains(i))
              .findFirst();
        }
      }

      @Override
      public List<User> findAll() {
        synchronized (library) {
          return List.copyOf(users.values());
        }
      }
    };
  }

  /** The catalog, as the audit decorators and the live feed read it; nothing else is asked. */
  ICatalogRepository catalogRepository() {
    DemoLibrary library = this;
    return (ICatalogRepository)
        Proxy.newProxyInstance(
            ICatalogRepository.class.getClassLoader(),
            new Class<?>[] {ICatalogRepository.class},
            (proxy, method, args) -> {
              synchronized (library) {
                return switch (method.getName()) {
                  case "getCatalog" -> CatalogIndex.fromSnapshot(catalog());
                  case "findFile" -> Optional.ofNullable(files.get((ItemId) args[0]));
                  case "toString" -> "demo catalog";
                  case "hashCode" -> System.identityHashCode(proxy);
                  case "equals" -> proxy == args[0];
                  default ->
                      throw new UnsupportedOperationException(
                          "The demo catalog does not answer " + method.getName());
                };
              }
            });
  }

  /** The audit trail, in memory. */
  IAuditLog auditLog() {
    DemoLibrary library = this;
    return new IAuditLog() {
      @Override
      public void append(AuditEntry e) {
        synchronized (library) {
          trail.add(e);
        }
      }

      @Override
      public List<AuditEntry> find(AuditQuery q) {
        synchronized (library) {
          return trail.stream()
              .filter(q::matches)
              .sorted(Comparator.comparing(AuditEntry::at).reversed())
              .limit(q.limit())
              .toList();
        }
      }
    };
  }

  /** Nothing to roll back in memory: runs the work on the spot. */
  static IUnitOfWork unitOfWork() {
    return new IUnitOfWork() {
      @Override
      public <T> T execute(Supplier<T> work) {
        return work.get();
      }
    };
  }

  /** The event bus: tells the observers about each change as soon as it happens. */
  IEventSubscriber eventBus() {
    return new IEventSubscriber() {
      @Override
      public void subscribe(ICatalogObserver observer) {
        observers.add(observer);
      }

      @Override
      public void unsubscribe(ICatalogObserver observer) {
        observers.remove(observer);
      }
    };
  }

  private void publish(CatalogEvent e) {
    for (ICatalogObserver observer : observers) {
      observer.onCatalogChanged(e);
    }
  }

  // ------------------------------------------------------------------ IAccessControl

  @Override
  public synchronized SessionToken authenticateUser(Credentials c) {
    if (c instanceof LocalCredentials local) {
      Optional<User> user = userRepository().findByUsername(local.username());
      if (user.isPresent()
          && user.get().toSnapshot().active()
          && local.password().equals(passwords.get(local.username()))) {
        UserSnapshot snapshot = user.get().toSnapshot();
        return issue(snapshot.id(), snapshot.role());
      }
    }
    throw new InvalidCredentials(
        "Wrong username or password (the demo has admin, ana and luis; password = username)");
  }

  @Override
  public synchronized Optional<SessionToken> validateToken(String value) {
    return Optional.ofNullable(tokens.get(value))
        .filter(token -> token.expiresAt().isAfter(Instant.now()))
        .filter(token ->
            Optional.ofNullable(users.get(token.userId()))
                .map(u -> u.toSnapshot().active())
                .orElse(false));
  }

  @Override
  public synchronized void logout(String value) {
    tokens.remove(value);
  }

  @Override
  public synchronized SessionToken accessWithLink(String link) {
    SharedLinkSnapshot shared = links.get(link);
    if (shared == null) {
      throw new ResourceNotFound("No shared link with that token");
    }
    return issue(shared.guestId(), Role.GUEST);
  }

  @Override
  public synchronized boolean authorizeResource(UserId u, ItemId r, AccessType acc) {
    return can(u, r, acc);
  }

  @Override
  public synchronized SharedLinkSnapshot shareCollection(
      UserId u, ItemId col, PermissionLevel level, Duration lifetime) {
    collection(col);
    require(u, col, AccessType.DELETE, "Only who manages a collection can share it");
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    User guest = User.createGuest(col, level);
    users.put(guest.toSnapshot().id(), guest);
    SharedLinkSnapshot link =
        new SharedLinkSnapshot(
            new SharedLinkId(UUID.randomUUID()),
            token,
            col,
            guest.toSnapshot().id(),
            level,
            u,
            Instant.now().plus(lifetime),
            false);
    links.put(token, link);
    return link;
  }

  @Override
  public synchronized UserId createUser(UserId admin, String username, String password, Role role) {
    requireAdmin(admin);
    if (username.isBlank() || password.isBlank()) {
      throw new IllegalArgumentException("A new user needs a username and a password");
    }
    if (passwords.containsKey(username)) {
      throw new IllegalStateException("There is already a user called " + username);
    }
    UserId created = account(username, role);
    passwords.put(username, password);
    return created;
  }

  @Override
  public synchronized void manageRoles(UserId admin, UserId target, Role role) {
    requireAdmin(admin);
    if (admin.equals(target)) {
      throw new IllegalStateException("You cannot change your own role");
    }
    user(target).changeRole(role);
  }

  @Override
  public synchronized void grantPermission(UserId granter, UserId u, Permission p) {
    requireDelegation(granter, u, p);
    user(u).grant(p);
  }

  @Override
  public synchronized void revokePermission(UserId granter, UserId u, Permission p) {
    requireDelegation(granter, u, p);
    user(u).revoke(p);
  }

  @Override
  public synchronized void deactivateUser(UserId admin, UserId u) {
    requireAdmin(admin);
    if (admin.equals(u)) {
      throw new IllegalStateException("You cannot deactivate yourself");
    }
    user(u).deactivate();
    tokens.values().removeIf(token -> token.userId().equals(u));
  }

  @Override
  public synchronized List<UserSnapshot> listUsers(UserId admin) {
    requireAdmin(admin);
    return users.values().stream()
        .map(User::toSnapshot)
        .filter(u -> u.role() != Role.GUEST)
        .sorted(Comparator.comparing(UserSnapshot::username))
        .toList();
  }

  @Override
  public synchronized UserId findUser(UserId u, String username) {
    UserSnapshot asking = user(u).toSnapshot();
    boolean managesSomething =
        asking.permissions().stream().anyMatch(p -> p.level() == PermissionLevel.MANAGE);
    if (asking.role() != Role.ADMIN && !managesSomething) {
      throw new AccessDenied("Only administrators and managers look people up");
    }
    return users.values().stream()
        .map(User::toSnapshot)
        .filter(s -> s.active() && s.role() != Role.GUEST && s.username().equals(username))
        .map(UserSnapshot::id)
        .findFirst()
        .orElseThrow(() -> new ResourceNotFound("Nobody is called " + username));
  }

  @Override
  public synchronized List<AccessGrant> accessTo(UserId u, ItemId resource) {
    require(u, resource, AccessType.DELETE, "Only who manages it can see who has access");
    List<AccessGrant> grants = new ArrayList<>();
    for (User user : users.values()) {
      UserSnapshot s = user.toSnapshot();
      if (!s.active() || s.role() == Role.GUEST) {
        continue;
      }
      s.permissions().stream()
          .filter(p -> p.appliesTo(resource))
          .findFirst()
          .ifPresent(p -> grants.add(new AccessGrant(s.id(), s.username(), p.level())));
    }
    grants.sort(Comparator.comparing(AccessGrant::username));
    return grants;
  }

  // ------------------------------------------------------------------ IAuditTrail

  @Override
  public synchronized List<AuditEntry> history(UserId u, AuditQuery q) {
    if (!user(u).isAdmin()) {
      ItemId resource =
          q.resource().orElseThrow(() -> new AccessDenied("Ask for the history of something"));
      require(u, resource, AccessType.VIEW, "You cannot see that");
    }
    return auditLog().find(q);
  }

  // ------------------------------------------------------------------ ICatalogManagement

  @Override
  public IngestionResult ingestFile(UserId u, Path path) {
    try (InputStream data = Files.newInputStream(path)) {
      return uploadFile(u, path.getFileName().toString(), data);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public synchronized IngestionResult uploadFile(UserId u, String fileName, InputStream data) {
    User uploader = registered(u);
    byte[] bytes;
    try {
      bytes = data.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    Fingerprint fingerprint = fingerprint(bytes);
    for (MediaFileSnapshot file : files.values()) {
      if (file.fingerprint().equals(fingerprint)) {
        return new IngestionResult(file.id(), true, Optional.of(DuplicateAction.REJECT));
      }
    }
    ItemId id = add(fileName, bytes, wavDuration(bytes));
    manageWhatYouMake(uploader, id);
    publish(new FileIngested(id, Instant.now()));
    return new IngestionResult(id, false, Optional.empty());
  }

  @Override
  public int reindexLibrary(UserId u, Path root) {
    requireAdmin(u);
    return 0;
  }

  @Override
  public List<MediaFileSnapshot> detectDuplicates(UserId u) {
    return List.of();
  }

  @Override
  public synchronized void editMetadata(UserId u, ItemId id, String key, String value) {
    MediaFileSnapshot file = file(id);
    require(u, id, AccessType.MODIFY, "You cannot change this file");
    List<MetadataEntry> metadata = new ArrayList<>(file.metadata());
    metadata.removeIf(entry -> entry.key().equals(key));
    metadata.add(new MetadataEntry(key, value, MetadataSource.MANUAL));
    files.put(id, withMetadataAndTags(file, metadata, file.tags()));
    publish(new FileModified(id, Instant.now()));
  }

  @Override
  public synchronized void deleteFile(UserId u, ItemId id) {
    file(id);
    require(u, id, AccessType.DELETE, "You cannot delete this file");
    files.remove(id);
    contents.remove(id);
    collections.replaceAll((key, c) ->
        new CollectionSnapshot(
            c.id(),
            c.name(),
            c.description(),
            c.items().stream().filter(item -> !item.equals(id)).toList()));
    publish(new FileDeleted(id, Instant.now()));
  }

  @Override
  public Path backupCatalog(UserId admin, Path destination) {
    requireAdmin(admin);
    return destination.resolve("catalog-demo.json");
  }

  @Override
  public void configureLibraryPath(UserId admin, Path path) {
    requireAdmin(admin);
  }

  @Override
  public synchronized ServerStatus monitorServerStatus(UserId admin) {
    requireAdmin(admin);
    long active =
        sessions.values().stream()
            .filter(s -> s.toSnapshot().status() != SessionStatus.FINISHED)
            .count();
    return new ServerStatus(files.size(), Path.of(".").toFile().getUsableSpace(), (int) active);
  }

  // ------------------------------------------------------------------ ISearch

  @Override
  public synchronized List<CatalogItemSnapshot> searchContent(UserId u, String txt) {
    String text = txt.toLowerCase(Locale.ROOT);
    List<CatalogItemSnapshot> found = new ArrayList<>();
    collections.values().stream()
        .filter(c -> c.name().toLowerCase(Locale.ROOT).contains(text))
        .filter(c -> can(u, c.id(), AccessType.VIEW))
        .forEach(found::add);
    files.values().stream()
        .filter(f -> f.name().toLowerCase(Locale.ROOT).contains(text) || hasTag(f, text))
        .filter(f -> can(u, f.id(), AccessType.VIEW))
        .forEach(found::add);
    return found;
  }

  @Override
  public synchronized List<MediaFileSnapshot> filterByCriteria(UserId u, SearchCriteria c) {
    return files.values().stream()
        .filter(f -> c.type().map(type -> f.type() == type).orElse(true))
        .filter(f ->
            c.text()
                .map(t -> f.name().toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT)))
                .orElse(true))
        .filter(f -> c.tags().stream().allMatch(tag -> hasTag(f, tag.normalized())))
        .filter(f -> can(u, f.id(), AccessType.VIEW))
        .toList();
  }

  /**
   * The top of the library: what is not inside any collection, among what the user can see. In the
   * demo a user also finds here what they were given access to inside a collection they cannot
   * open, so that a shared collection is always reachable.
   */
  @Override
  public synchronized List<CatalogItemSnapshot> browseLibrary(UserId u) {
    Set<ItemId> nested = new HashSet<>();
    collections.values().forEach(c -> nested.addAll(c.items()));
    List<CatalogItemSnapshot> top = new ArrayList<>();
    for (CatalogItemSnapshot item : everything()) {
      if (!can(u, item.id(), AccessType.VIEW)) {
        continue;
      }
      boolean reachable =
          !nested.contains(item.id())
              || collections.values().stream()
                  .anyMatch(c -> c.items().contains(item.id()) && can(u, c.id(), AccessType.VIEW));
      if (!nested.contains(item.id()) || !reachable) {
        top.add(item);
      }
    }
    return top;
  }

  @Override
  public synchronized List<CatalogItemSnapshot> browseLibrary(UserId u, ItemId col) {
    CollectionSnapshot collection = collection(col);
    require(u, col, AccessType.VIEW, "You cannot open this collection");
    List<CatalogItemSnapshot> items = new ArrayList<>();
    for (ItemId id : collection.items()) {
      if (collections.containsKey(id)) {
        items.add(collections.get(id));
      } else if (files.containsKey(id)) {
        items.add(files.get(id));
      }
    }
    return items;
  }

  @Override
  public synchronized CollectionSnapshot manageCollections(UserId u, CollectionCommand cmd) {
    User asking = registered(u);
    for (ItemId item : cmd.itemsToAdd()) {
      if (!files.containsKey(item) && !collections.containsKey(item)) {
        throw new ResourceNotFound("No file or collection " + item.value());
      }
      require(u, item, AccessType.VIEW, "You cannot add what you cannot see");
    }
    CollectionSnapshot result;
    CatalogEvent event;
    if (cmd.collectionId().isEmpty()) {
      ItemId id = new ItemId(UUID.randomUUID());
      result = new CollectionSnapshot(id, cmd.name().orElseThrow(), "", cmd.itemsToAdd());
      collections.put(id, result);
      manageWhatYouMake(asking, id);
      event = new CollectionCreated(id, Instant.now());
    } else {
      CollectionSnapshot current = collection(cmd.collectionId().get());
      require(u, current.id(), AccessType.MODIFY, "You cannot change this collection");
      List<ItemId> items = new ArrayList<>(current.items());
      cmd.itemsToAdd().stream().filter(item -> !items.contains(item)).forEach(items::add);
      result =
          new CollectionSnapshot(
              current.id(), cmd.name().orElse(current.name()), current.description(), items);
      collections.put(result.id(), result);
      event = new CollectionModified(result.id(), Instant.now());
    }
    publish(event);
    return result;
  }

  @Override
  public synchronized void tagFile(UserId u, ItemId id, Tag e) {
    MediaFileSnapshot file = file(id);
    require(u, id, AccessType.MODIFY, "You cannot tag this file");
    Set<Tag> tags = new HashSet<>(file.tags());
    tags.add(e);
    files.put(id, withMetadataAndTags(file, file.metadata(), tags));
    publish(new FileModified(id, Instant.now()));
  }

  // ------------------------------------------------------------------ IPlayback

  @Override
  public synchronized PlaybackSessionSnapshot openSession(UserId u, ItemId id) {
    MediaFileSnapshot file = file(id);
    require(u, id, AccessType.VIEW, "You cannot play this file");
    PlaybackSession session = PlaybackSession.start(id, u, file.duration(), Instant.now());
    PlaybackSessionSnapshot snapshot = session.toSnapshot();
    sessions.put(snapshot.id(), session);
    return snapshot;
  }

  @Override
  public synchronized PlaybackSessionSnapshot resumeFromLastPosition(UserId u, ItemId id) {
    Optional<PlaybackSession> last =
        sessions.values().stream()
            .filter(s -> s.toSnapshot().userId().equals(u) && s.toSnapshot().fileId().equals(id))
            .max(Comparator.comparing(s -> s.toSnapshot().startedAt()));
    PlaybackSessionSnapshot opened = openSession(u, id);
    if (last.isPresent() && last.get().canResume()) {
      PlaybackSession session = sessions.get(opened.id());
      session.advance(last.get().toSnapshot().position());
      return session.toSnapshot();
    }
    return opened;
  }

  @Override
  public synchronized MediaStream requestStream(UserId u, SessionId s, StreamProfile p) {
    ItemId fileId = session(u, s).toSnapshot().fileId();
    byte[] bytes = contents.get(fileId);
    return new MediaStream(
        new ByteArrayInputStream(bytes),
        mime(file(fileId).name()),
        false,
        Optional.of((long) bytes.length));
  }

  @Override
  public synchronized void recordProgress(UserId u, SessionId s, Duration pos) {
    PlaybackSession session = session(u, s);
    if (session.toSnapshot().status() != SessionStatus.FINISHED) {
      session.advance(pos.minus(session.toSnapshot().position()));
    }
  }

  @Override
  public synchronized void pauseSession(UserId u, SessionId s) {
    session(u, s).pause();
  }

  @Override
  public synchronized void resumeSession(UserId u, SessionId s) {
    session(u, s).resume();
  }

  @Override
  public synchronized void finishSession(UserId u, SessionId s) {
    session(u, s).finish();
  }

  // ------------------------------------------------------------------ ISync

  @Override
  public SyncDelta syncChanges(UserId u, Instant since) {
    return new SyncDelta(List.of(), List.of(), List.of());
  }

  @Override
  public synchronized OfflinePackage cacheContent(UserId u, ItemId id) {
    MediaFileSnapshot file = file(id);
    require(u, id, AccessType.DOWNLOAD, "You cannot download this file");
    try {
      Path folder = Files.createTempDirectory("omnistream-demo");
      Path copy = Files.write(folder.resolve(file.name()), contents.get(id));
      return new OfflinePackage(id, file.metadata(), copy);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public boolean hasPendingChanges(UserId u, Instant since) {
    return false;
  }

  // ------------------------------------------------------------------ helpers

  private UserId account(String username, Role role) {
    User user =
        User.create(username, role, new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, username));
    UserId id = user.toSnapshot().id();
    users.put(id, user);
    passwords.put(username, username);
    return id;
  }

  private static AuditEntry seed(UserId actor, AuditAction action, Instant at) {
    return AuditEntry.of(actor, action, at);
  }

  private SessionToken issue(UserId user, Role role) {
    byte[] bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    SessionToken token =
        new SessionToken(value, user, role, Instant.now().plus(Duration.ofHours(12)));
    tokens.put(value, token);
    return token;
  }

  private User user(UserId id) {
    User user = users.get(id);
    if (user == null) {
      throw new ResourceNotFound("No user " + id.value());
    }
    return user;
  }

  /** A user with an account: guests only watch and listen. */
  private User registered(UserId id) {
    User user = user(id);
    if (user.toSnapshot().role() == Role.GUEST) {
      throw new AccessDenied("Guests can only watch and listen");
    }
    return user;
  }

  private void requireAdmin(UserId u) {
    if (!user(u).isAdmin()) {
      throw new AccessDenied("Only an administrator can do this");
    }
  }

  private CatalogSnapshot catalog() {
    return new CatalogSnapshot(
        CATALOG, "Demo", 1, List.copyOf(files.values()), List.copyOf(collections.values()));
  }

  private boolean can(UserId u, ItemId r, AccessType access) {
    User user = users.get(u);
    return user != null && policy.authorizes(user, r, access, catalog());
  }

  private void require(UserId u, ItemId r, AccessType access, String refusal) {
    if (!can(u, r, access)) {
      throw new AccessDenied(refusal);
    }
  }

  private void requireDelegation(UserId granter, UserId u, Permission p) {
    User from = user(granter);
    User to = user(u);
    Optional<PermissionLevel> inherited = policy.effectiveLevel(from, p.resource(), catalog());
    if (!policy.mayDelegate(from, inherited, to, p)) {
      throw new AccessDenied("You cannot give or take that permission (ADR 0024)");
    }
  }

  /** In the demo, whoever adds a file or a collection manages it. */
  private void manageWhatYouMake(User maker, ItemId made) {
    if (!maker.isAdmin()) {
      maker.grant(new Permission(made, PermissionLevel.MANAGE));
    }
  }

  private List<CatalogItemSnapshot> everything() {
    List<CatalogItemSnapshot> all = new ArrayList<>(collections.values());
    all.addAll(files.values());
    return all;
  }

  private MediaFileSnapshot file(ItemId id) {
    MediaFileSnapshot file = files.get(id);
    if (file == null) {
      throw new ResourceNotFound("No file " + id.value());
    }
    return file;
  }

  private CollectionSnapshot collection(ItemId id) {
    CollectionSnapshot collection = collections.get(id);
    if (collection == null) {
      throw new ResourceNotFound("No collection " + id.value());
    }
    return collection;
  }

  private PlaybackSession session(UserId u, SessionId s) {
    PlaybackSession session = sessions.get(s);
    if (session == null) {
      throw new ResourceNotFound("No session " + s.value());
    }
    if (!session.toSnapshot().userId().equals(u)) {
      throw new AccessDenied("This session belongs to someone else");
    }
    return session;
  }

  private ItemId add(String name, byte[] bytes, Duration duration) {
    ItemId id = new ItemId(UUID.randomUUID());
    files.put(
        id,
        new MediaFileSnapshot(
            id,
            name,
            Path.of("/demo", name),
            fingerprint(bytes),
            type(name),
            bytes.length,
            duration,
            IndexingStatus.INDEXED,
            Instant.now(),
            List.of(),
            Set.of()));
    contents.put(id, bytes);
    return id;
  }

  private static MediaFileSnapshot withMetadataAndTags(
      MediaFileSnapshot f, List<MetadataEntry> metadata, Set<Tag> tags) {
    return new MediaFileSnapshot(
        f.id(),
        f.name(),
        f.path(),
        f.fingerprint(),
        f.type(),
        f.sizeBytes(),
        f.duration(),
        f.status(),
        f.ingestedAt(),
        metadata,
        tags);
  }

  private static boolean hasTag(MediaFileSnapshot file, String normalized) {
    return file.tags().stream().anyMatch(tag -> tag.normalized().contains(normalized));
  }

  private static String extension(String name) {
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static MediaType type(String name) {
    return switch (extension(name)) {
      case "mp3", "wav", "ogg", "oga", "flac", "m4a", "aac" -> MediaType.AUDIO;
      case "mp4", "webm", "mkv", "mov", "m4v" -> MediaType.VIDEO;
      case "png", "jpg", "jpeg", "gif", "webp", "svg" -> MediaType.IMAGE;
      default -> MediaType.DOCUMENT;
    };
  }

  private static String mime(String name) {
    return switch (extension(name)) {
      case "mp3" -> "audio/mpeg";
      case "wav" -> "audio/wav";
      case "ogg", "oga" -> "audio/ogg";
      case "flac" -> "audio/flac";
      case "m4a", "aac" -> "audio/mp4";
      case "mp4", "m4v" -> "video/mp4";
      case "webm" -> "video/webm";
      case "mkv" -> "video/x-matroska";
      case "mov" -> "video/quicktime";
      case "png" -> "image/png";
      case "jpg", "jpeg" -> "image/jpeg";
      case "gif" -> "image/gif";
      case "webp" -> "image/webp";
      case "svg" -> "image/svg+xml";
      case "pdf" -> "application/pdf";
      case "txt" -> "text/plain";
      default -> "application/octet-stream";
    };
  }

  private static Fingerprint fingerprint(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      return new Fingerprint("SHA-256", HexFormat.of().formatHex(digest));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The length of a PCM WAV file, from its header; zero for anything else. */
  private static Duration wavDuration(byte[] bytes) {
    if (bytes.length < 44 || !new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")) {
      return Duration.ZERO;
    }
    ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    int byteRate = header.getInt(28);
    return byteRate <= 0
        ? Duration.ZERO
        : Duration.ofMillis((bytes.length - 44) * 1000L / byteRate);
  }

  /** A sine tone as a 16-bit mono PCM WAV file. */
  private static byte[] tone(int frequency, int seconds) {
    int rate = 22_050;
    int samples = rate * seconds;
    ByteBuffer wav = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN);
    wav.put("RIFF".getBytes(StandardCharsets.US_ASCII))
        .putInt(36 + samples * 2)
        .put("WAVE".getBytes(StandardCharsets.US_ASCII));
    wav.put("fmt ".getBytes(StandardCharsets.US_ASCII))
        .putInt(16)
        .putShort((short) 1)
        .putShort((short) 1);
    wav.putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16);
    wav.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(samples * 2);
    for (int i = 0; i < samples; i++) {
      double fade = Math.min(1.0, Math.min(i, samples - i) / (rate * 0.05));
      wav.putShort((short) (Math.sin(2 * Math.PI * frequency * i / rate) * 8_000 * fade));
    }
    return wav.array();
  }

  /** A small gradient as a PNG file. */
  private static byte[] gradient() {
    BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setPaint(new GradientPaint(0, 0, new Color(0x1f6f8b), 320, 180, new Color(0xf6c85f)));
    g.fillRect(0, 0, 320, 180);
    g.dispose();
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, "png", png);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return png.toByteArray();
  }
}
