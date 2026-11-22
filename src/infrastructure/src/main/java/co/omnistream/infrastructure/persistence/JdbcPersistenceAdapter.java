package co.omnistream.infrastructure.persistence;

import co.omnistream.domain.event.CatalogEvent;
import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.event.EventId;
import co.omnistream.domain.exception.VersionConflict;
import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.model.playback.PlaybackSession;
import co.omnistream.domain.model.playback.SessionId;
import co.omnistream.domain.model.sharing.SharedLink;
import co.omnistream.domain.port.dto.AuditQuery;
import co.omnistream.domain.port.dto.SearchCriteria;
import co.omnistream.domain.port.dto.SyncDelta;
import co.omnistream.domain.port.out.IAuditLog;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.IChangeLog;
import co.omnistream.domain.port.out.IConfigurationRepository;
import co.omnistream.domain.port.out.IDataBackup;
import co.omnistream.domain.port.out.IOutboxRepository;
import co.omnistream.domain.port.out.ISessionRepository;
import co.omnistream.domain.port.out.ISharedLinkRepository;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import co.omnistream.domain.snapshot.PlaybackSessionSnapshot;
import co.omnistream.domain.snapshot.SharedLinkSnapshot;
import co.omnistream.domain.snapshot.UserSnapshot;
import co.omnistream.infrastructure.database.Database;
import co.omnistream.infrastructure.database.PersistenceException;
import co.omnistream.infrastructure.database.Schema;
import co.omnistream.infrastructure.database.Sql;
import co.omnistream.infrastructure.persistence.mapper.AuditEntryMapper;
import co.omnistream.infrastructure.persistence.mapper.CatalogIndexMapper;
import co.omnistream.infrastructure.persistence.mapper.CollectionMapper;
import co.omnistream.infrastructure.persistence.mapper.EventMapper;
import co.omnistream.infrastructure.persistence.mapper.IdentityMapper;
import co.omnistream.infrastructure.persistence.mapper.MediaFileMapper;
import co.omnistream.infrastructure.persistence.mapper.MetadataMapper;
import co.omnistream.infrastructure.persistence.mapper.PermissionMapper;
import co.omnistream.infrastructure.persistence.mapper.SessionMapper;
import co.omnistream.infrastructure.persistence.mapper.SharedLinkMapper;
import co.omnistream.infrastructure.persistence.mapper.UserMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Secondary adapter that keeps every aggregate in a relational database with plain JDBC (ADR 0015).
 *
 * <p>It implements all the persistence driven ports at once, as the diagram says: the catalog, the
 * sessions, the users, the shared links, the change log, the outbox, the unit of work, the audit
 * trail, the backup and the configuration. {@link SqlitePersistenceAdapter} and {@link
 * PostgresPersistenceAdapter} only change how the database is reached and dumped.
 *
 * <p>Every method opens a connection or joins the transaction of the {@link IUnitOfWork} running on
 * the same thread. {@link #save(CatalogIndex)} writes the aggregate and the events it pulled in the
 * same transaction (Transactional Outbox).
 */
public abstract class JdbcPersistenceAdapter
    implements ICatalogRepository,
        ISessionRepository,
        IUserRepository,
        ISharedLinkRepository,
        IChangeLog,
        IOutboxRepository,
        IUnitOfWork,
        IAuditLog,
        IDataBackup,
        IConfigurationRepository {

  private static final String CATALOG = "SELECT * FROM catalog";
  private static final String FILES_OF_CATALOG = "SELECT * FROM files WHERE catalog_id = ?";

  private final Database database;
  private final CatalogIndexMapper catalogMapper = new CatalogIndexMapper();
  private final MediaFileMapper mediaFileMapper = new MediaFileMapper();
  private final MetadataMapper metadataMapper = new MetadataMapper();
  private final CollectionMapper collectionMapper = new CollectionMapper();
  private final UserMapper userMapper = new UserMapper();
  private final PermissionMapper permissionMapper = new PermissionMapper();
  private final IdentityMapper identityMapper = new IdentityMapper();
  private final SessionMapper sessionMapper = new SessionMapper();
  private final SharedLinkMapper sharedLinkMapper = new SharedLinkMapper();
  private final EventMapper eventMapper = new EventMapper();
  private final AuditEntryMapper auditEntryMapper = new AuditEntryMapper();

  protected JdbcPersistenceAdapter(Database database) {
    this.database = Objects.requireNonNull(database, "database");
    database.inTransaction(() -> {
      Schema.create(database.connection());
      return null;
    });
  }

  /** The database, so a subclass can dump it in its own format and the factory can close it. */
  public final Database database() {
    return database;
  }

  // ------------------------------------------------------------------ catalog

  @Override
  public CatalogIndex getCatalog() {
    return withConnection(connection -> {
      Optional<CatalogSnapshot> stored =
          Sql.one(connection, CATALOG, row -> catalogMapper.toSnapshot(row, List.of(), List.of()));
      if (stored.isEmpty()) {
        return CatalogIndex.fromSnapshot(catalogMapper.empty());
      }
      CatalogSnapshot idAndVersion = stored.get();
      String catalogId = idAndVersion.id().value().toString();
      List<MediaFileSnapshot> files =
          Sql.list(
              connection,
              FILES_OF_CATALOG + " ORDER BY name",
              row -> file(connection, row),
              catalogId);
      List<CollectionSnapshot> collections =
          Sql.list(
              connection,
              "SELECT * FROM collections WHERE catalog_id = ? ORDER BY name",
              row -> collection(connection, row),
              catalogId);
      return CatalogIndex.fromSnapshot(
          new CatalogSnapshot(
              idAndVersion.id(), idAndVersion.name(), idAndVersion.version(), files, collections));
    });
  }

  @Override
  public void save(CatalogIndex catalog) {
    Objects.requireNonNull(catalog, "catalog");
    CatalogSnapshot snapshot = catalog.toSnapshot();
    List<CatalogEvent> events = catalog.pullEvents();
    inTransaction(() -> {
      Connection connection = database.connection();
      try {
        writeCatalog(connection, snapshot);
        for (CatalogEvent event : events) {
          appendEvent(connection, "outbox", event, "published", 0);
        }
        return null;
      } finally {
        database.release(connection);
      }
    });
  }

  @Override
  public Optional<MediaFileSnapshot> findFile(ItemId id) {
    Objects.requireNonNull(id, "id");
    return withConnection(connection ->
        Sql.one(
            connection,
            "SELECT * FROM files WHERE id = ?",
            row -> file(connection, row),
            id.value().toString()));
  }

  @Override
  public Optional<MediaFileSnapshot> findByFingerprint(Fingerprint h) {
    Objects.requireNonNull(h, "h");
    return withConnection(connection ->
            Sql.one(
                connection,
                "SELECT * FROM files WHERE fingerprint_algorithm = ? AND fingerprint_value = ?",
                row -> row.getString("id"),
                h.algorithm(),
                h.value()))
        .flatMap(id -> findFile(new ItemId(UUID.fromString(id))));
  }

  @Override
  public List<MediaFileSnapshot> findByText(String text) {
    Objects.requireNonNull(text, "text");
    String wanted = text.strip().toLowerCase(Locale.ROOT);
    if (wanted.isEmpty()) {
      return List.of();
    }
    return allFiles().stream().filter(file -> matchesText(file, wanted)).toList();
  }

  @Override
  public List<MediaFileSnapshot> findByCriteria(SearchCriteria c) {
    Objects.requireNonNull(c, "c");
    String text = c.text().map(value -> value.strip().toLowerCase(Locale.ROOT)).orElse("");
    return allFiles().stream()
        .filter(file -> text.isEmpty() || matchesText(file, text))
        .filter(file -> c.type().map(type -> file.type() == type).orElse(true))
        .filter(file -> hasAllTags(file, c.tags()))
        .toList();
  }

  @Override
  public void append(AuditEntry e) {
    Objects.requireNonNull(e, "e");
    withConnection(connection -> {
      Sql.update(
          connection,
          """
          INSERT INTO audit_entries
            (id, happened_at, actor, action, resource, subject, detail)
          VALUES (?, ?, ?, ?, ?, ?, ?)
          """,
          e.id().value().toString(),
          e.at().toEpochMilli(),
          e.actor().value().toString(),
          e.action().name(),
          e.resource().map(r -> r.value().toString()).orElse(null),
          e.subject().map(u -> u.value().toString()).orElse(null),
          e.detail());
      return null;
    });
  }

  @Override
  public List<AuditEntry> find(AuditQuery q) {
    Objects.requireNonNull(q, "q");
    return withConnection(connection -> {
      StringBuilder sql = new StringBuilder("SELECT * FROM audit_entries WHERE 1 = 1");
      List<Object> parameters = new ArrayList<>();
      q.actor()
          .ifPresent(actor -> {
            sql.append(" AND actor = ?");
            parameters.add(actor.value().toString());
          });
      q.resource()
          .ifPresent(resource -> {
            sql.append(" AND resource = ?");
            parameters.add(resource.value().toString());
          });
      q.subject()
          .ifPresent(subject -> {
            sql.append(" AND subject = ?");
            parameters.add(subject.value().toString());
          });
      if (!q.actions().isEmpty()) {
        sql.append(" AND action IN (")
            .append(q.actions().stream().map(a -> "?").collect(Collectors.joining(", ")))
            .append(")");
        q.actions().stream().map(AuditAction::name).forEach(parameters::add);
      }
      q.from()
          .ifPresent(from -> {
            sql.append(" AND happened_at >= ?");
            parameters.add(from.toEpochMilli());
          });
      q.to()
          .ifPresent(to -> {
            sql.append(" AND happened_at < ?");
            parameters.add(to.toEpochMilli());
          });
      sql.append(" ORDER BY happened_at DESC LIMIT ?");
      parameters.add(q.limit());
      return Sql.list(connection, sql.toString(), auditEntryMapper::toValue, parameters.toArray());
    });
  }

  // ------------------------------------------------------------------ users

  @Override
  public void save(User u) {
    Objects.requireNonNull(u, "u");
    UserSnapshot s = u.toSnapshot();
    inTransaction(() -> {
      Connection connection = database.connection();
      try {
        String id = s.id().value().toString();
        Sql.updateOrInsert(
            connection,
            "UPDATE users SET username = ?, role = ?, active = ? WHERE id = ?",
            new Object[] {s.username(), s.role().name(), s.active() ? 1 : 0, id},
            "INSERT INTO users (username, role, active, id) VALUES (?, ?, ?, ?)",
            new Object[] {s.username(), s.role().name(), s.active() ? 1 : 0, id});
        Sql.update(connection, "DELETE FROM user_permissions WHERE user_id = ?", id);
        for (Permission permission : s.permissions()) {
          Sql.update(
              connection,
              "INSERT INTO user_permissions (user_id, resource_id, level) VALUES (?, ?, ?)",
              id,
              permission.resource().value().toString(),
              permission.level().name());
        }
        Sql.update(connection, "DELETE FROM user_identities WHERE user_id = ?", id);
        for (LinkedIdentity identity : s.identities()) {
          Sql.update(
              connection,
              "INSERT INTO user_identities (user_id, issuer, subject) VALUES (?, ?, ?)",
              id,
              identity.issuer(),
              identity.subject());
        }
        return null;
      } finally {
        database.release(connection);
      }
    });
  }

  @Override
  public Optional<User> findById(UserId id) {
    Objects.requireNonNull(id, "id");
    return withConnection(connection ->
            Sql.one(
                connection,
                "SELECT * FROM users WHERE id = ?",
                row -> user(connection, row),
                id.value().toString()))
        .map(User::fromSnapshot);
  }

  @Override
  public Optional<User> findByUsername(String username) {
    Objects.requireNonNull(username, "username");
    return withConnection(connection ->
            Sql.one(
                connection,
                "SELECT * FROM users WHERE username = ?",
                row -> user(connection, row),
                username.strip()))
        .map(User::fromSnapshot);
  }

  @Override
  public Optional<User> findByIdentity(LinkedIdentity i) {
    Objects.requireNonNull(i, "i");
    return withConnection(connection ->
            Sql.one(
                connection,
                "SELECT user_id FROM user_identities WHERE issuer = ? AND subject = ?",
                row -> row.getString("user_id"),
                i.issuer(),
                i.subject()))
        .flatMap(id -> findById(new UserId(UUID.fromString(id))));
  }

  @Override
  public List<User> findAll() {
    return withConnection(connection ->
        Sql.list(connection, "SELECT * FROM users ORDER BY username", row -> user(connection, row))
            .stream()
            .map(User::fromSnapshot)
            .toList());
  }

  // ------------------------------------------------------------------ sessions

  @Override
  public void save(PlaybackSession session) {
    Objects.requireNonNull(session, "session");
    PlaybackSessionSnapshot s = session.toSnapshot();
    withConnection(connection -> {
      Sql.updateOrInsert(
          connection,
          """
          UPDATE playback_sessions
             SET file_id = ?, user_id = ?, position_nanos = ?, total_nanos = ?, status = ?,
                 started_at = ?
           WHERE id = ?
          """,
          new Object[] {
            s.fileId().value().toString(),
            s.userId().value().toString(),
            s.position().toNanos(),
            s.totalDuration().toNanos(),
            s.status().name(),
            s.startedAt().toEpochMilli(),
            s.id().value().toString()
          },
          """
          INSERT INTO playback_sessions
            (file_id, user_id, position_nanos, total_nanos, status, started_at, id)
          VALUES (?, ?, ?, ?, ?, ?, ?)
          """,
          new Object[] {
            s.fileId().value().toString(),
            s.userId().value().toString(),
            s.position().toNanos(),
            s.totalDuration().toNanos(),
            s.status().name(),
            s.startedAt().toEpochMilli(),
            s.id().value().toString()
          });
      return null;
    });
  }

  @Override
  public Optional<PlaybackSession> findById(SessionId id) {
    Objects.requireNonNull(id, "id");
    return withConnection(connection ->
            Sql.one(
                connection,
                "SELECT * FROM playback_sessions WHERE id = ?",
                sessionMapper::toSnapshot,
                id.value().toString()))
        .map(PlaybackSession::fromSnapshot);
  }

  @Override
  public Optional<PlaybackSession> findLastSession(UserId u, ItemId a) {
    Objects.requireNonNull(u, "u");
    Objects.requireNonNull(a, "a");
    return withConnection(connection ->
            Sql.one(
                connection,
                """
                SELECT * FROM playback_sessions
                 WHERE user_id = ? AND file_id = ?
                 ORDER BY started_at DESC
                 LIMIT 1
                """,
                sessionMapper::toSnapshot,
                u.value().toString(),
                a.value().toString()))
        .map(PlaybackSession::fromSnapshot);
  }

  @Override
  public int countActive() {
    return withConnection(connection ->
        Math.toIntExact(
            Sql.count(
                connection,
                "SELECT COUNT(*) FROM playback_sessions WHERE status IN ('ACTIVE', 'PAUSED')",
                0)));
  }

  // ------------------------------------------------------------------ shared links

  @Override
  public void save(SharedLink link) {
    Objects.requireNonNull(link, "link");
    SharedLinkSnapshot s = link.toSnapshot();
    withConnection(connection -> {
      Sql.updateOrInsert(
          connection,
          """
          UPDATE shared_links
             SET token = ?, collection_id = ?, guest_id = ?, level = ?, created_by = ?,
                 expires_at = ?, revoked = ?
           WHERE id = ?
          """,
          new Object[] {
            s.token(),
            s.collectionId().value().toString(),
            s.guestId().value().toString(),
            s.level().name(),
            s.createdBy().value().toString(),
            s.expiresAt().toEpochMilli(),
            s.revoked() ? 1 : 0,
            s.id().value().toString()
          },
          """
          INSERT INTO shared_links
            (token, collection_id, guest_id, level, created_by, expires_at, revoked, id)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          """,
          new Object[] {
            s.token(),
            s.collectionId().value().toString(),
            s.guestId().value().toString(),
            s.level().name(),
            s.createdBy().value().toString(),
            s.expiresAt().toEpochMilli(),
            s.revoked() ? 1 : 0,
            s.id().value().toString()
          });
      return null;
    });
  }

  @Override
  public Optional<SharedLink> findByToken(String token) {
    Objects.requireNonNull(token, "token");
    return withConnection(connection ->
            Sql.one(
                connection,
                "SELECT * FROM shared_links WHERE token = ?",
                sharedLinkMapper::toSnapshot,
                token))
        .map(SharedLink::fromSnapshot);
  }

  // ------------------------------------------------------------------ change log and outbox

  @Override
  public void register(CatalogEvent e) {
    Objects.requireNonNull(e, "e");
    withConnection(connection -> {
      appendEvent(connection, "change_log", e, null, null);
      return null;
    });
  }

  @Override
  public SyncDelta changesSince(Instant since) {
    Objects.requireNonNull(since, "since");
    return withConnection(connection -> {
      List<CatalogEvent> events =
          Sql.list(
              connection,
              "SELECT * FROM change_log WHERE occurred_at >= ? ORDER BY occurred_at",
              eventMapper::toCatalogEvent,
              since.toEpochMilli());
      Set<ItemId> added = new LinkedHashSet<>();
      Set<ItemId> modified = new LinkedHashSet<>();
      Set<ItemId> removed = new LinkedHashSet<>();
      for (CatalogEvent event : events) {
        switch (event.changeType()) {
          case ADDED -> added.add(event.item());
          case MODIFIED -> modified.add(event.item());
          case REMOVED -> removed.add(event.item());
        }
      }
      return new SyncDelta(List.copyOf(added), List.copyOf(modified), List.copyOf(removed));
    });
  }

  @Override
  public boolean hasChangesSince(Instant since) {
    Objects.requireNonNull(since, "since");
    return withConnection(connection ->
        Sql.count(
                connection,
                "SELECT COUNT(*) FROM change_log WHERE occurred_at >= ?",
                0,
                since.toEpochMilli())
            > 0);
  }

  @Override
  public List<DomainEvent> fetchPending(int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("A limit must be positive");
    }
    return withConnection(connection ->
        Sql.list(
            connection,
            "SELECT * FROM outbox WHERE published = 0 ORDER BY occurred_at LIMIT ?",
            eventMapper::toEvent,
            limit));
  }

  @Override
  public void markPublished(EventId id) {
    Objects.requireNonNull(id, "id");
    withConnection(connection -> {
      Sql.update(
          connection, "UPDATE outbox SET published = 1 WHERE event_id = ?", id.value().toString());
      return null;
    });
  }

  // ------------------------------------------------------------------ unit of work, backup,
  // configuration

  @Override
  public <T> T execute(Supplier<T> work) {
    return inTransaction(work);
  }

  @Override
  public Path backup(Path destination) {
    Objects.requireNonNull(destination, "destination");
    Path target = destination;
    if (Files.isDirectory(destination)) {
      target = destination.resolve("omnistream-" + Instant.now().toEpochMilli() + ".dump");
    }
    Path parent = target.toAbsolutePath().getParent();
    if (parent != null) {
      try {
        Files.createDirectories(parent);
      } catch (IOException e) {
        throw new PersistenceException("Cannot create the backup folder " + parent, e);
      }
    }
    writeDump(target);
    return target;
  }

  @Override
  public Path getLibraryPath() {
    return withConnection(connection ->
        Sql.one(
                connection,
                "SELECT setting_value FROM settings WHERE setting_key = 'library.path'",
                row -> row.getString(1))
            .map(Path::of)
            .orElseGet(() -> Path.of(System.getProperty("user.home"), ".omnistream", "library")));
  }

  @Override
  public void saveLibraryPath(Path path) {
    Objects.requireNonNull(path, "path");
    withConnection(connection -> {
      Sql.updateOrInsert(
          connection,
          "UPDATE settings SET setting_value = ? WHERE setting_key = 'library.path'",
          new Object[] {path.toString()},
          "INSERT INTO settings (setting_key, setting_value) VALUES ('library.path', ?)",
          new Object[] {path.toString()});
      return null;
    });
  }

  // ------------------------------------------------------------------ shared helpers

  /** Writes the database in the format of its vendor; the subclasses know how. */
  protected abstract void writeDump(Path destination);

  private void writeCatalog(Connection connection, CatalogSnapshot snapshot) {
    String id = snapshot.id().value().toString();
    Optional<Long> storedVersion =
        Sql.one(connection, "SELECT version FROM catalog WHERE id = ?", row -> row.getLong(1), id);
    if (storedVersion.isEmpty()) {
      if (snapshot.version() != 0) {
        throw new VersionConflict(
            "The catalog " + id + " is not stored but its version is " + snapshot.version());
      }
      Sql.update(
          connection,
          "INSERT INTO catalog (id, name, version) VALUES (?, ?, ?)",
          id,
          snapshot.name(),
          1L);
    } else {
      if (storedVersion.get() != snapshot.version()) {
        throw new VersionConflict(
            "The catalog changed: stored version is "
                + storedVersion.get()
                + ", loaded version was "
                + snapshot.version());
      }
      Sql.update(
          connection,
          "UPDATE catalog SET name = ?, version = ? WHERE id = ?",
          snapshot.name(),
          storedVersion.get() + 1,
          id);
    }

    Sql.update(
        connection,
        "DELETE FROM file_metadata WHERE file_id IN (SELECT id FROM files WHERE catalog_id = ?)",
        id);
    Sql.update(
        connection,
        "DELETE FROM file_tags WHERE file_id IN (SELECT id FROM files WHERE catalog_id = ?)",
        id);
    Sql.update(connection, "DELETE FROM files WHERE catalog_id = ?", id);
    Sql.update(
        connection,
        """
        DELETE FROM collection_items
         WHERE collection_id IN (SELECT id FROM collections WHERE catalog_id = ?)
        """,
        id);
    Sql.update(connection, "DELETE FROM collections WHERE catalog_id = ?", id);

    for (MediaFileSnapshot file : snapshot.files()) {
      insertFile(connection, id, file);
    }
    for (CollectionSnapshot collection : snapshot.collections()) {
      Sql.update(
          connection,
          """
          INSERT INTO collections (id, catalog_id, name, description)
          VALUES (?, ?, ?, ?)
          """,
          collection.id().value().toString(),
          id,
          collection.name(),
          collection.description());
      int order = 0;
      for (ItemId item : collection.items()) {
        Sql.update(
            connection,
            """
            INSERT INTO collection_items (collection_id, item_id, item_order)
            VALUES (?, ?, ?)
            """,
            collection.id().value().toString(),
            item.value().toString(),
            order++);
      }
    }
  }

  private void insertFile(Connection connection, String catalogId, MediaFileSnapshot file) {
    Sql.update(
        connection,
        """
        INSERT INTO files
          (id, catalog_id, name, path, fingerprint_algorithm, fingerprint_value, type,
           size_bytes, duration_nanos, status, ingested_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        file.id().value().toString(),
        catalogId,
        file.name(),
        file.path().toString(),
        file.fingerprint().algorithm(),
        file.fingerprint().value(),
        file.type().name(),
        file.sizeBytes(),
        file.duration().toNanos(),
        file.status().name(),
        file.ingestedAt().toEpochMilli());
    for (MetadataEntry metadata : file.metadata()) {
      Sql.update(
          connection,
          """
          INSERT INTO file_metadata (file_id, entry_key, entry_value, source)
          VALUES (?, ?, ?, ?)
          """,
          file.id().value().toString(),
          metadata.key(),
          metadata.value(),
          metadata.source().name());
    }
    for (Tag tag : file.tags()) {
      Sql.update(
          connection,
          "INSERT INTO file_tags (file_id, name, source) VALUES (?, ?, ?)",
          file.id().value().toString(),
          tag.name(),
          tag.source().name());
    }
  }

  private void appendEvent(
      Connection connection,
      String table,
      CatalogEvent event,
      String extraColumn,
      Object extraValue) {
    String eventId = event.id().value().toString();
    if (Sql.count(connection, "SELECT COUNT(*) FROM " + table + " WHERE event_id = ?", 0, eventId)
        > 0) {
      return;
    }
    if (extraColumn == null) {
      Sql.update(
          connection,
          """
          INSERT INTO change_log (event_id, occurred_at, event_type, item_id)
          VALUES (?, ?, ?, ?)
          """,
          eventId,
          event.occurredAt().toEpochMilli(),
          event.type(),
          event.item().value().toString());
    } else {
      Sql.update(
          connection,
          """
          INSERT INTO outbox (event_id, occurred_at, event_type, item_id, published)
          VALUES (?, ?, ?, ?, ?)
          """,
          eventId,
          event.occurredAt().toEpochMilli(),
          event.type(),
          event.item().value().toString(),
          extraValue);
    }
  }

  private MediaFileSnapshot file(Connection connection, ResultSet row) throws SQLException {
    String id = row.getString("id");
    List<MetadataEntry> metadata =
        Sql.list(
            connection,
            "SELECT * FROM file_metadata WHERE file_id = ? ORDER BY entry_key",
            metadataMapper::toValue,
            id);
    Set<Tag> tags =
        new LinkedHashSet<>(
            Sql.list(
                connection,
                "SELECT * FROM file_tags WHERE file_id = ? ORDER BY name",
                mediaFileMapper::tag,
                id));
    return mediaFileMapper.toSnapshot(row, metadata, tags);
  }

  private CollectionSnapshot collection(Connection connection, ResultSet row) throws SQLException {
    String id = row.getString("id");
    List<ItemId> items =
        Sql.list(
            connection,
            "SELECT item_id FROM collection_items WHERE collection_id = ? ORDER BY item_order",
            item -> new ItemId(UUID.fromString(item.getString(1))),
            id);
    return collectionMapper.toSnapshot(row, items);
  }

  private UserSnapshot user(Connection connection, ResultSet row) throws SQLException {
    String id = row.getString("id");
    Set<Permission> permissions =
        new LinkedHashSet<>(
            Sql.list(
                connection,
                "SELECT * FROM user_permissions WHERE user_id = ?",
                permissionMapper::toValue,
                id));
    Set<LinkedIdentity> identities =
        new LinkedHashSet<>(
            Sql.list(
                connection,
                "SELECT * FROM user_identities WHERE user_id = ?",
                identityMapper::toValue,
                id));
    return userMapper.toSnapshot(row, permissions, identities);
  }

  private List<MediaFileSnapshot> allFiles() {
    return withConnection(connection ->
        Sql.list(connection, "SELECT * FROM files ORDER BY name", row -> file(connection, row)));
  }

  private static boolean matchesText(MediaFileSnapshot file, String wanted) {
    if (file.name().toLowerCase(Locale.ROOT).contains(wanted)) {
      return true;
    }
    if (file.metadata().stream()
        .anyMatch(entry ->
            entry.key().toLowerCase(Locale.ROOT).contains(wanted)
                || entry.value().toLowerCase(Locale.ROOT).contains(wanted))) {
      return true;
    }
    return file.tags().stream().anyMatch(tag -> tag.normalized().contains(wanted));
  }

  private static boolean hasAllTags(MediaFileSnapshot file, Set<Tag> wanted) {
    return wanted.stream()
        .allMatch(expected ->
            file.tags().stream().anyMatch(tag -> tag.normalized().equals(expected.normalized())));
  }

  private <T> T withConnection(Function<Connection, T> work) {
    Connection connection = database.connection();
    try {
      return work.apply(connection);
    } finally {
      database.release(connection);
    }
  }

  private <T> T inTransaction(Supplier<T> work) {
    return database.inTransaction(work);
  }
}
