package co.omnistream.presentation.rest;

import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.LocalCredentials;
import co.omnistream.domain.port.dto.SessionToken;
import co.omnistream.domain.port.in.IAccessControl;
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
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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

  public RestApiAdapter(
      ICatalogManagement catalogManagement,
      IPlayback playback,
      ISearch search,
      IAccessControl accessControl,
      ISync sync) {
    this.catalogManagement = Objects.requireNonNull(catalogManagement, "catalogManagement");
    this.playback = Objects.requireNonNull(playback, "playback");
    this.search = Objects.requireNonNull(search, "search");
    this.accessControl = Objects.requireNonNull(accessControl, "accessControl");
    this.sync = Objects.requireNonNull(sync, "sync");
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
    routes.post(
        API + "/users/{id}/permissions",
        ctx -> {
          UserId admin = user(ctx);
          Permission permission = ctx.bodyAsClass(Permission.class);
          accessControl.grantPermission(admin, userId(ctx, "id"), permission);
          ctx.status(204);
        });
    routes.delete(
        API + "/users/{id}/permissions/{resource}",
        ctx -> {
          UserId admin = user(ctx);
          PermissionLevel level = PermissionLevel.valueOf(requiredQuery(ctx, "level"));
          accessControl.revokePermission(
              admin, userId(ctx, "id"), new Permission(itemId(ctx, "resource"), level));
          ctx.status(204);
        });
    routes.post(
        API + "/users/{id}/deactivate",
        ctx -> {
          UserId admin = user(ctx);
          accessControl.deactivateUser(admin, userId(ctx, "id"));
          ctx.status(204);
        });
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

  /** Body of {@code PUT /users/{id}/role}. */
  record RoleRequest(Role role) {
    RoleRequest {
      required(role, "role");
    }
  }
}
