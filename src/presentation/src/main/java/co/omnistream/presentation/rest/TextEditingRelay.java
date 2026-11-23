package co.omnistream.presentation.rest;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.SessionToken;
import co.omnistream.domain.port.in.IAccessControl;
import co.omnistream.domain.port.in.ITextEditing;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsContext;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Primary adapter that relays the edits of the collaborative editor over a WebSocket at {@value
 * #PATH} (ADR 0027). Opening and saving go through {@link ITextEditing}; the rooms and the protocol
 * are in {@link EditingRooms}.
 *
 * <p>The handshake is refused before the upgrade when the request has no valid session (401) or
 * comes from a page of another site (403, checked with the {@code Origin} header, which browsers
 * always send). Once connected, a file that cannot be opened closes the connection with 4000 plus
 * the HTTP status the REST API would answer: 4403, 4404 or 4422.
 */
public final class TextEditingRelay {

  /** Where the editor connects; {@code id} is the file. */
  public static final String PATH = RestApiAdapter.API + "/files/{id}/edit";

  /** Largest message: a whole document of 1 MiB with its history, with room to spare. */
  static final long MAX_MESSAGE_BYTES = 8L * 1024 * 1024;

  private static final String USER = "omnistream.user";
  private static final Logger LOG = LoggerFactory.getLogger(TextEditingRelay.class);

  private final IAccessControl accessControl;
  private final EditingRooms rooms;
  private final Map<String, Browser> browsers = new ConcurrentHashMap<>();

  public TextEditingRelay(ITextEditing textEditing, IAccessControl accessControl) {
    this.accessControl = Objects.requireNonNull(accessControl, "accessControl");
    this.rooms =
        new EditingRooms(
            textEditing,
            e -> {
              ApiErrors.ErrorResponse response = ApiErrors.toResponse(e);
              if (response.status() >= 500) {
                LOG.error("Unexpected error in the text editor relay", e);
              }
              return new EditingRooms.Failure(
                  response.status(), response.body().error(), response.body().message());
            });
  }

  /**
   * Registers the WebSocket on a Javalin configuration, and raises the size Jetty accepts for one
   * message to {@link #MAX_MESSAGE_BYTES} (its default is 64 KiB, less than a text file may have).
   */
  public void registerRoutes(JavalinConfig config) {
    config.jetty.modifyWebSocketServletFactory(factory -> {
      factory.setMaxTextMessageSize(MAX_MESSAGE_BYTES);
      factory.setMaxBinaryMessageSize(MAX_MESSAGE_BYTES);
    });
    config.routes.wsBeforeUpgrade(PATH, this::handshake);
    config.routes.ws(
        PATH,
        ws -> {
          ws.onConnect(this::connect);
          ws.onBinaryMessage(ctx -> {
            Browser browser = browsers.get(ctx.sessionId());
            if (browser != null) {
              rooms.receive(browser, bytes(ctx.data()));
            }
          });
          ws.onMessage(ctx -> {
            Browser browser = browsers.get(ctx.sessionId());
            if (browser != null) {
              browser.send(
                  Map.of(
                      "type",
                      "error",
                      "error",
                      "BadRequest",
                      "message",
                      "Edits are binary frames"));
            }
          });
          ws.onClose(this::disconnect);
          ws.onError(this::disconnect);
        });
  }

  /** Who is connecting, checked before the connection becomes a WebSocket. */
  private void handshake(Context ctx) {
    String origin = ctx.header("Origin");
    if (origin != null && !sameOrigin(origin, ctx.host())) {
      throw new ForbiddenResponse("The editor only accepts connections from this server's pages");
    }
    String token = RestApiAdapter.tokenValue(ctx);
    if (token == null || token.isBlank()) {
      throw new UnauthorizedResponse("Missing session token");
    }
    SessionToken session =
        accessControl
            .validateToken(token)
            .orElseThrow(() -> new UnauthorizedResponse("Invalid or expired session token"));
    ctx.attribute(USER, session.userId());
  }

  private void connect(WsConnectContext ctx) {
    UserId user = ctx.attribute(USER);
    ItemId file;
    try {
      file = new ItemId(RestApiAdapter.uuid(ctx.pathParam("id"), "id"));
    } catch (IllegalArgumentException e) {
      ctx.closeSession(4400, "Not a file identifier");
      return;
    }
    Browser browser = new Browser(ctx, user);
    browsers.put(ctx.sessionId(), browser);
    ctx.enableAutomaticPings();
    rooms.join(file, browser);
  }

  private void disconnect(WsContext ctx) {
    Browser browser = browsers.remove(ctx.sessionId());
    if (browser != null) {
      ctx.disableAutomaticPings();
      rooms.leave(browser);
    }
  }

  /** Whether {@code origin} names the same host and port the request was sent to. */
  static boolean sameOrigin(String origin, String host) {
    if (host == null) {
      return false;
    }
    try {
      String authority = URI.create(origin).getAuthority();
      return authority != null && authority.equalsIgnoreCase(host);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static byte[] bytes(ByteBuffer data) {
    ByteBuffer copy = data.duplicate();
    byte[] bytes = new byte[copy.remaining()];
    copy.get(bytes);
    return bytes;
  }

  /** One browser tab in a room. */
  private static final class Browser implements EditingRooms.Peer {
    private final WsConnectContext ctx;
    private final UserId user;

    Browser(WsConnectContext ctx, UserId user) {
      this.ctx = ctx;
      this.user = user;
    }

    @Override
    public UserId user() {
      return user;
    }

    @Override
    public void send(Map<String, Object> message) {
      ctx.send(ApiJson.mapper().writeValueAsString(message));
    }

    @Override
    public void send(byte[] frame) {
      ctx.send(ByteBuffer.wrap(frame));
    }

    @Override
    public void close(int code, String reason) {
      ctx.closeSession(code, reason);
    }
  }
}
