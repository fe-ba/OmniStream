package co.omnistream.presentation.rest;

import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.catalog.IngestionResult;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.model.catalog.TagSource;
import co.omnistream.domain.model.playback.SessionId;
import co.omnistream.domain.port.dto.AuditQuery;
import co.omnistream.domain.port.dto.CollectionCommand;
import co.omnistream.domain.port.dto.LocalCredentials;
import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.OfflinePackage;
import co.omnistream.domain.port.dto.SearchCriteria;
import co.omnistream.domain.port.dto.SessionToken;
import co.omnistream.domain.port.dto.StreamProfile;
import co.omnistream.domain.port.in.FeedSubscription;
import co.omnistream.domain.port.in.IAccessControl;
import co.omnistream.domain.port.in.IAuditTrail;
import co.omnistream.domain.port.in.ICatalogFeed;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.domain.port.in.IPlayback;
import co.omnistream.domain.port.in.ISearch;
import co.omnistream.domain.port.in.ISync;
import io.javalin.config.JavalinConfig;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.http.UploadedFile;
import io.javalin.http.sse.SseClient;
import io.javalin.http.sse.SseHandler;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Primary adapter that serves the REST API: it turns each request into a call to a driving port and
 * the result into JSON. The routes, their bodies and their errors are the contract in {@code
 * docs/reference/rest-api.md}.
 *
 * <p>The adapter has no rules of its own: who may do what is decided by the domain. It only finds
 * out who is asking ({@link #authenticateRequest}) and passes that user to the port.
 */
public final class RestApiAdapter {

  /** Prefix of every route (ADR 0023). */
  static final String API = "/api/v1";

  /** Cookie that carries the session token for the web UI. */
  static final String TOKEN_COOKIE = "omnistream_token";

  private final ICatalogManagement catalogManagement;
  private final IPlayback playback;
  private final ISearch search;
  private final IAccessControl accessControl;
  private final ISync sync;
  private final IAuditTrail auditTrail;
  private final ICatalogFeed catalogFeed;

  public RestApiAdapter(
      ICatalogManagement catalogManagement,
      IPlayback playback,
      ISearch search,
      IAccessControl accessControl,
      ISync sync,
      IAuditTrail auditTrail,
      ICatalogFeed catalogFeed) {
    this.catalogManagement = Objects.requireNonNull(catalogManagement, "catalogManagement");
    this.playback = Objects.requireNonNull(playback, "playback");
    this.search = Objects.requireNonNull(search, "search");
    this.accessControl = Objects.requireNonNull(accessControl, "accessControl");
    this.sync = Objects.requireNonNull(sync, "sync");
    this.auditTrail = Objects.requireNonNull(auditTrail, "auditTrail");
    this.catalogFeed = Objects.requireNonNull(catalogFeed, "catalogFeed");
  }

  /**
   * Registers the API on a Javalin configuration: its JSON conventions ({@link ApiJson}), its error
   * responses ({@link ApiErrors}) and its routes. In Javalin 7 routes are registered before the
   * server starts, so this is called inside {@code Javalin.create(config -> ...)}.
   */
  public void registerRoutes(JavalinConfig config) {
    config.jsonMapper(ApiJson.forJavalin());
    ApiErrors.register(config);
    accessRoutes(config.routes);
    catalogRoutes(config.routes);
    searchRoutes(config.routes);
    playbackRoutes(config.routes);
    syncRoutes(config.routes);
    auditRoutes(config.routes);
    feedRoutes(config.routes);
  }

  // ------------------------------------------------------------------ access (IAccessControl)

  private void accessRoutes(RoutesConfig routes) {
    routes.post(
        API + "/auth/login",
        ctx -> {
          LoginRequest body = ctx.bodyAsClass(LoginRequest.class);
          SessionToken token =
              accessControl.authenticateUser(
                  new LocalCredentials(body.provider(), body.username(), body.password()));
          startSession(ctx, token);
        });
    routes.post(
        API + "/auth/logout",
        ctx -> {
          authenticateRequest(ctx);
          accessControl.logout(tokenValue(ctx));
          ctx.removeCookie(TOKEN_COOKIE, "/");
          ctx.status(204);
        });
    routes.post(
        API + "/links/{token}/access",
        ctx -> startSession(ctx, accessControl.accessWithLink(ctx.pathParam("token"))));
    routes.post(
        API + "/collections/{id}/links",
        ctx -> {
          UserId u = user(ctx);
          ShareRequest body = ctx.bodyAsClass(ShareRequest.class);
          ctx.status(201)
              .json(
                  accessControl.shareCollection(
                      u, itemId(ctx, "id"), body.level(), body.lifetime()));
        });
    routes.post(
        API + "/users",
        ctx -> {
          UserId admin = user(ctx);
          NewUserRequest body = ctx.bodyAsClass(NewUserRequest.class);
          UserId created =
              accessControl.createUser(admin, body.username(), body.password(), body.role());
          ctx.status(201).json(Map.of("id", created));
        });
    routes.put(
        API + "/users/{id}/role",
        ctx -> {
          UserId admin = user(ctx);
          RoleRequest body = ctx.bodyAsClass(RoleRequest.class);
          accessControl.manageRoles(admin, userId(ctx, "id"), body.role());
          ctx.status(204);
        });
    // Administrators, and whoever manages the resource (ADR 0024)
    routes.post(
        API + "/users/{id}/permissions",
        ctx -> {
          UserId granter = user(ctx);
          Permission permission = ctx.bodyAsClass(Permission.class);
          accessControl.grantPermission(granter, userId(ctx, "id"), permission);
          ctx.status(204);
        });
    routes.delete(
        API + "/users/{id}/permissions/{resource}",
        ctx -> {
          UserId granter = user(ctx);
          PermissionLevel level = PermissionLevel.valueOf(requiredQuery(ctx, "level"));
          accessControl.revokePermission(
              granter, userId(ctx, "id"), new Permission(itemId(ctx, "resource"), level));
          ctx.status(204);
        });
    routes.post(
        API + "/users/{id}/deactivate",
        ctx -> {
          UserId admin = user(ctx);
          accessControl.deactivateUser(admin, userId(ctx, "id"));
          ctx.status(204);
        });
    routes.get(API + "/users", ctx -> ctx.json(accessControl.listUsers(user(ctx))));
    routes.get(
        API + "/users/by-name/{username}",
        ctx -> {
          UserId u = user(ctx);
          UserId found = accessControl.findUser(u, ctx.pathParam("username"));
          ctx.json(Map.of("id", found));
        });
    routes.get(
        API + "/items/{id}/access",
        ctx -> {
          UserId u = user(ctx);
          ctx.json(accessControl.accessTo(u, itemId(ctx, "id")));
        });
  }

  // ------------------------------------------------------------------ catalog (ICatalogManagement)

  private void catalogRoutes(RoutesConfig routes) {
    routes.post(
        API + "/files",
        ctx -> {
          UserId u = user(ctx);
          UploadedFile file = ctx.uploadedFile("file");
          if (file == null) {
            throw new IllegalArgumentException("Missing the multipart field 'file'");
          }
          IngestionResult result;
          try (InputStream content = file.content()) {
            result = catalogManagement.uploadFile(u, file.filename(), content);
          }
          ctx.status(result.wasIndexed() ? 201 : 200).json(result);
        });
    routes.get(
        API + "/files/duplicates", ctx -> ctx.json(catalogManagement.detectDuplicates(user(ctx))));
    routes.put(
        API + "/files/{id}/metadata/{key}",
        ctx -> {
          UserId u = user(ctx);
          ValueRequest body = ctx.bodyAsClass(ValueRequest.class);
          catalogManagement.editMetadata(u, itemId(ctx, "id"), ctx.pathParam("key"), body.value());
          ctx.status(204);
        });
    routes.delete(
        API + "/files/{id}",
        ctx -> {
          catalogManagement.deleteFile(user(ctx), itemId(ctx, "id"));
          ctx.status(204);
        });
    routes.post(
        API + "/admin/reindex",
        ctx -> {
          UserId admin = user(ctx);
          ReindexRequest body = ctx.bodyAsClass(ReindexRequest.class);
          ctx.json(Map.of("indexed", catalogManagement.reindexLibrary(admin, body.root())));
        });
    routes.post(
        API + "/admin/backup",
        ctx -> {
          UserId admin = user(ctx);
          BackupRequest body = ctx.bodyAsClass(BackupRequest.class);
          ctx.json(Map.of("path", catalogManagement.backupCatalog(admin, body.destination())));
        });
    routes.put(
        API + "/admin/library-path",
        ctx -> {
          UserId admin = user(ctx);
          LibraryPathRequest body = ctx.bodyAsClass(LibraryPathRequest.class);
          catalogManagement.configureLibraryPath(admin, body.path());
          ctx.status(204);
        });
    routes.get(
        API + "/admin/status", ctx -> ctx.json(catalogManagement.monitorServerStatus(user(ctx))));
  }

  // ------------------------------------------------------------------ search (ISearch)

  private void searchRoutes(RoutesConfig routes) {
    routes.get(
        API + "/search",
        ctx -> {
          UserId u = user(ctx);
          ctx.json(search.searchContent(u, requiredQuery(ctx, "q")));
        });
    routes.get(
        API + "/files",
        ctx -> {
          UserId u = user(ctx);
          Optional<MediaType> type = optionalQuery(ctx, "type").map(MediaType::valueOf);
          Set<Tag> tags =
              ctx.queryParams("tag").stream()
                  .filter(name -> !name.isBlank())
                  .map(name -> new Tag(name, TagSource.MANUAL))
                  .collect(Collectors.toSet());
          ctx.json(
              search.filterByCriteria(
                  u, new SearchCriteria(optionalQuery(ctx, "text"), type, tags)));
        });
    routes.get(API + "/library", ctx -> ctx.json(search.browseLibrary(user(ctx))));
    routes.get(
        API + "/collections/{id}/items",
        ctx -> {
          UserId u = user(ctx);
          ctx.json(search.browseLibrary(u, itemId(ctx, "id")));
        });
    routes.post(
        API + "/collections",
        ctx -> {
          UserId u = user(ctx);
          CollectionRequest body = ctx.bodyAsClass(CollectionRequest.class);
          String name = required(body.name(), "name");
          CollectionCommand create =
              new CollectionCommand(Optional.empty(), Optional.of(name), body.itemsToAdd());
          ctx.status(201).json(search.manageCollections(u, create));
        });
    routes.patch(
        API + "/collections/{id}",
        ctx -> {
          UserId u = user(ctx);
          CollectionRequest body = ctx.bodyAsClass(CollectionRequest.class);
          CollectionCommand change =
              new CollectionCommand(
                  Optional.of(itemId(ctx, "id")),
                  Optional.ofNullable(body.name()),
                  body.itemsToAdd());
          ctx.json(search.manageCollections(u, change));
        });
    routes.post(
        API + "/files/{id}/tags",
        ctx -> {
          UserId u = user(ctx);
          TagRequest body = ctx.bodyAsClass(TagRequest.class);
          search.tagFile(u, itemId(ctx, "id"), new Tag(body.name(), TagSource.MANUAL));
          ctx.status(204);
        });
  }

  // ------------------------------------------------------------------ playback (IPlayback)

  private void playbackRoutes(RoutesConfig routes) {
    routes.post(
        API + "/files/{id}/sessions",
        ctx -> {
          UserId u = user(ctx);
          ctx.status(201).json(playback.openSession(u, itemId(ctx, "id")));
        });
    routes.post(
        API + "/files/{id}/sessions/resume",
        ctx -> {
          UserId u = user(ctx);
          ctx.status(201).json(playback.resumeFromLastPosition(u, itemId(ctx, "id")));
        });
    routes.get(
        API + "/sessions/{id}/stream",
        ctx -> {
          UserId u = user(ctx);
          sendStream(ctx, playback.requestStream(u, sessionId(ctx, "id"), streamProfile(ctx)));
        });
    routes.put(
        API + "/sessions/{id}/position",
        ctx -> {
          UserId u = user(ctx);
          PositionRequest body = ctx.bodyAsClass(PositionRequest.class);
          playback.recordProgress(u, sessionId(ctx, "id"), body.position());
          ctx.status(204);
        });
    routes.post(
        API + "/sessions/{id}/pause",
        ctx -> {
          playback.pauseSession(user(ctx), sessionId(ctx, "id"));
          ctx.status(204);
        });
    routes.post(
        API + "/sessions/{id}/resume",
        ctx -> {
          playback.resumeSession(user(ctx), sessionId(ctx, "id"));
          ctx.status(204);
        });
    routes.post(
        API + "/sessions/{id}/finish",
        ctx -> {
          playback.finishSession(user(ctx), sessionId(ctx, "id"));
          ctx.status(204);
        });
  }

  /**
   * Sends media to the client. A file sent as it is has a known size, so it answers HTTP range
   * requests and the player can jump to any position; a transcoded stream is sent as it is
   * produced, starting at the session's position.
   */
  private static void sendStream(Context ctx, MediaStream stream) {
    if (!stream.transcoded() && stream.sizeBytes().isPresent()) {
      ctx.writeSeekableStream(stream.data(), stream.mime(), stream.sizeBytes().get());
    } else {
      ctx.contentType(stream.mime());
      ctx.result(stream.data());
    }
  }

  private static StreamProfile streamProfile(Context ctx) {
    return new StreamProfile(
        optionalQuery(ctx, "codec"),
        optionalQuery(ctx, "bitrateKbps").map(Integer::valueOf),
        optionalQuery(ctx, "resolution"));
  }

  // ------------------------------------------------------------------ synchronization (ISync)

  private void syncRoutes(RoutesConfig routes) {
    routes.get(
        API + "/sync/changes",
        ctx -> {
          UserId u = user(ctx);
          ctx.json(sync.syncChanges(u, instantQuery(ctx, "since")));
        });
    routes.get(
        API + "/sync/pending",
        ctx -> {
          UserId u = user(ctx);
          ctx.json(Map.of("pending", sync.hasPendingChanges(u, instantQuery(ctx, "since"))));
        });
    routes.get(
        API + "/files/{id}/offline",
        ctx -> {
          UserId u = user(ctx);
          sendAttachment(ctx, sync.cacheContent(u, itemId(ctx, "id")));
        });
  }

  // ------------------------------------------------------------------ audit trail (IAuditTrail)

  /** Entries a query returns when it does not say how many. */
  static final int DEFAULT_AUDIT_LIMIT = 100;

  private void auditRoutes(RoutesConfig routes) {
    routes.get(
        API + "/audit",
        ctx -> {
          UserId u = user(ctx);
          ctx.json(auditTrail.history(u, auditQuery(ctx)));
        });
  }

  /** Every filter is optional; {@code action} may be repeated (ADR 0025). */
  private static AuditQuery auditQuery(Context ctx) {
    Set<AuditAction> actions =
        ctx.queryParams("action").stream()
            .filter(value -> !value.isBlank())
            .map(AuditAction::valueOf)
            .collect(Collectors.toSet());
    return new AuditQuery(
        optionalQuery(ctx, "actor").map(value -> new UserId(uuid(value, "actor"))),
        optionalQuery(ctx, "resource").map(value -> new ItemId(uuid(value, "resource"))),
        optionalQuery(ctx, "subject").map(value -> new UserId(uuid(value, "subject"))),
        actions,
        optionalQuery(ctx, "from").map(value -> instant(value, "from")),
        optionalQuery(ctx, "to").map(value -> instant(value, "to")),
        optionalQuery(ctx, "limit").map(Integer::valueOf).orElse(DEFAULT_AUDIT_LIMIT));
  }

  // ------------------------------------------------------------------ live changes (ICatalogFeed)

  /** Media type of a stream of server-sent events. */
  static final String EVENT_STREAM = "text/event-stream";

  /**
   * A stream of server-sent events (ADR 0026): {@code ready} when it opens, then one {@code change}
   * with a {@code CatalogChange} for each change the user can see. The browser's {@code
   * EventSource} reconnects by itself if the connection drops, and gets a new {@code ready}: the
   * web UI reloads what it shows then, so it misses nothing in between.
   */
  private void feedRoutes(RoutesConfig routes) {
    routes.get(
        API + "/events",
        ctx -> {
          UserId u = user(ctx);
          String accept = ctx.header("Accept");
          if (accept == null || !accept.contains(EVENT_STREAM)) {
            throw new IllegalArgumentException(
                "The live changes are a stream: ask with 'Accept: " + EVENT_STREAM + "'");
          }
          // Follow before the response starts, so an error can still answer with its status
          AtomicReference<SseClient> stream = new AtomicReference<>();
          FeedSubscription subscription =
              catalogFeed.follow(
                  u,
                  change -> {
                    SseClient client = stream.get();
                    if (client != null) {
                      client.sendEvent("change", change);
                    }
                  });
          new SseHandler(
                  0,
                  client -> {
                    client.onClose(subscription::close);
                    client.keepAlive();
                    stream.set(client);
                    client.sendEvent("ready", Map.of());
                  })
              .handle(ctx);
        });
  }

  /** Sends the content of an offline package as a file to save; its metadata is in the snapshot. */
  private static void sendAttachment(Context ctx, OfflinePackage pack) throws IOException {
    Path content = pack.content();
    String fileName = content.getFileName().toString().replace("\"", "_");
    String type = Files.probeContentType(content);
    ctx.header("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
    ctx.header("Content-Length", Long.toString(Files.size(content)));
    ctx.contentType(type == null ? "application/octet-stream" : type);
    ctx.result(Files.newInputStream(content));
  }

  /** Answers a login with its token, and hands it to the web UI as an {@code HttpOnly} cookie. */
  private static void startSession(Context ctx, SessionToken token) {
    ctx.cookie(
        new Cookie(TOKEN_COOKIE, token.value(), "/", -1, false, true, null, SameSite.STRICT));
    ctx.json(token);
  }

  // ------------------------------------------------------------------ authentication

  /**
   * The session of the user making the request, from the {@code Authorization: Bearer} header or,
   * failing that, the cookie. Answers 401 if there is none or it is not valid.
   */
  private SessionToken authenticateRequest(Context ctx) {
    String value = tokenValue(ctx);
    if (value == null || value.isBlank()) {
      throw new UnauthorizedResponse("Missing session token");
    }
    return accessControl
        .validateToken(value)
        .orElseThrow(() -> new UnauthorizedResponse("Invalid or expired session token"));
  }

  private UserId user(Context ctx) {
    return authenticateRequest(ctx).userId();
  }

  private static String tokenValue(Context ctx) {
    String header = ctx.header("Authorization");
    String bearer = "Bearer ";
    if (header != null && header.regionMatches(true, 0, bearer, 0, bearer.length())) {
      return header.substring(bearer.length()).strip();
    }
    return ctx.cookie(TOKEN_COOKIE);
  }

  // ------------------------------------------------------------------ request parts

  private static ItemId itemId(Context ctx, String name) {
    return new ItemId(uuid(ctx.pathParam(name), name));
  }

  private static SessionId sessionId(Context ctx, String name) {
    return new SessionId(uuid(ctx.pathParam(name), name));
  }

  private static UserId userId(Context ctx, String name) {
    return new UserId(uuid(ctx.pathParam(name), name));
  }

  private static UUID uuid(String value, String name) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("'" + name + "' is not a valid identifier: " + value, e);
    }
  }

  private static String requiredQuery(Context ctx, String name) {
    String value = ctx.queryParam(name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing query parameter '" + name + "'");
    }
    return value;
  }

  private static Optional<String> optionalQuery(Context ctx, String name) {
    return Optional.ofNullable(ctx.queryParam(name)).filter(value -> !value.isBlank());
  }

  private static Instant instantQuery(Context ctx, String name) {
    return instant(requiredQuery(ctx, name), name);
  }

  private static Instant instant(String value, String name) {
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(
          "'" + name + "' must be an ISO-8601 instant, such as 2026-11-20T15:04:05Z: " + value, e);
    }
  }

  private static <T> T required(T value, String name) {
    if (value == null) {
      throw new IllegalArgumentException("Missing '" + name + "'");
    }
    return value;
  }

  // ------------------------------------------------------------------ request bodies

  /** Body of {@code POST /auth/login}; the provider is {@code "local"} when it is left out. */
  record LoginRequest(String provider, String username, String password) {
    LoginRequest {
      provider = provider == null ? "local" : provider;
      required(username, "username");
      required(password, "password");
    }
  }

  /** Body of {@code POST /collections/{id}/links}. */
  record ShareRequest(PermissionLevel level, Duration lifetime) {
    ShareRequest {
      required(level, "level");
      required(lifetime, "lifetime");
    }
  }

  /** Body of {@code POST /users}. */
  record NewUserRequest(String username, String password, Role role) {
    NewUserRequest {
      required(username, "username");
      required(password, "password");
      required(role, "role");
    }
  }

  /** Body of {@code PUT /files/{id}/metadata/{key}}. */
  record ValueRequest(String value) {
    ValueRequest {
      required(value, "value");
    }
  }

  /** Body of {@code POST /admin/reindex}. */
  record ReindexRequest(Path root) {
    ReindexRequest {
      required(root, "root");
    }
  }

  /** Body of {@code POST /admin/backup}. */
  record BackupRequest(Path destination) {
    BackupRequest {
      required(destination, "destination");
    }
  }

  /** Body of {@code PUT /admin/library-path}. */
  record LibraryPathRequest(Path path) {
    LibraryPathRequest {
      required(path, "path");
    }
  }

  /**
   * Body of {@code POST /collections}, where {@code name} is required, and of {@code PATCH
   * /collections/{id}}, where both fields may be left out.
   */
  record CollectionRequest(String name, List<ItemId> itemsToAdd) {
    CollectionRequest {
      itemsToAdd = itemsToAdd == null ? List.of() : List.copyOf(itemsToAdd);
    }
  }

  /** Body of {@code POST /files/{id}/tags}. */
  record TagRequest(String name) {
    TagRequest {
      required(name, "name");
    }
  }

  /** Body of {@code PUT /sessions/{id}/position}: seconds from the beginning of the file. */
  record PositionRequest(Duration position) {
    PositionRequest {
      if (required(position, "position").isNegative()) {
        throw new IllegalArgumentException("A position cannot be negative");
      }
    }
  }

  /** Body of {@code PUT /users/{id}/role}. */
  record RoleRequest(Role role) {
    RoleRequest {
      required(role, "role");
    }
  }
}
