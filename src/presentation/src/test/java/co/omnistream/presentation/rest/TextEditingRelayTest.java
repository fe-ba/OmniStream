package co.omnistream.presentation.rest;

import static co.omnistream.presentation.rest.FakeAccessControl.ADMIN;
import static co.omnistream.presentation.rest.FakeAccessControl.TOKEN;
import static co.omnistream.presentation.rest.TestHttp.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.TextDocument;
import co.omnistream.domain.port.in.ITextEditing;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TextEditingRelayTest {

  private static final String FILE = "00000000-0000-4000-8000-0000000000e1";
  private static final Fingerprint F1 = new Fingerprint("SHA-256", "1".repeat(64));
  private static final Fingerprint F2 = new Fingerprint("SHA-256", "2".repeat(64));

  /** Editing in memory: the admin may write; {@code failure}, if set, is what opening throws. */
  private static final class FakeEditing implements ITextEditing {
    volatile RuntimeException failure;
    volatile String saved;

    @Override
    public TextDocument open(UserId u, ItemId file) {
      if (failure != null) {
        throw failure;
      }
      return new TextDocument(file, "setlist.md", "# Setlist\n", F1, u.equals(ADMIN));
    }

    @Override
    public TextDocument save(UserId u, ItemId file, String text, Fingerprint basedOn) {
      saved = text;
      return new TextDocument(file, "setlist.md", text, F2, true);
    }
  }

  /** Collects what the server sends: text messages as they are, a close as "close <code>". */
  private static final class Inbox implements WebSocket.Listener {
    final BlockingQueue<String> received = new LinkedBlockingQueue<>();
    private final StringBuilder partial = new StringBuilder();

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
      partial.append(data);
      if (last) {
        received.add(partial.toString());
        partial.setLength(0);
      }
      ws.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
      received.add("close " + statusCode);
      return null;
    }

    String next() throws InterruptedException {
      String message = received.poll(5, TimeUnit.SECONDS);
      assertThat(message).as("a message within five seconds").isNotNull();
      return message;
    }
  }

  private final FakeEditing editing = new FakeEditing();

  private Javalin app() {
    TextEditingRelay relay = new TextEditingRelay(editing, new FakeAccessControl());
    return Javalin.create(relay::registerRoutes);
  }

  private static URI uri(Javalin server) {
    return URI.create("ws://localhost:" + server.port() + "/api/v1/files/" + FILE + "/edit");
  }

  private static WebSocket.Builder builder() {
    return HttpClient.newHttpClient().newWebSocketBuilder();
  }

  private static ByteBuffer frame(byte kind, String body) {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    ByteBuffer frame = ByteBuffer.allocate(bytes.length + 1);
    frame.put(kind).put(bytes).flip();
    return frame;
  }

  @Test
  void anEditorIsWelcomedAndCanSave() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          Inbox inbox = new Inbox();
          WebSocket ws =
              builder()
                  .header("Authorization", "Bearer " + TOKEN)
                  .header("Origin", "http://localhost:" + server.port())
                  .buildAsync(uri(server), inbox)
                  .get(5, TimeUnit.SECONDS);

          var welcome = json(inbox.next());
          assertThat(welcome.get("type").asString()).isEqualTo("welcome");
          assertThat(welcome.get("file").asString()).isEqualTo(FILE);
          assertThat(welcome.get("text").asString()).isEqualTo("# Setlist\n");
          assertThat(welcome.get("seed").asBoolean()).isTrue();
          assertThat(welcome.get("editable").asBoolean()).isTrue();
          assertThat(welcome.get("fingerprint").get("value").asString()).isEqualTo(F1.value());

          ws.sendBinary(frame(EditingRooms.UPDATE, "yjs update"), true).get(5, TimeUnit.SECONDS);
          ws.sendBinary(frame(EditingRooms.SAVE, "# Setlist ñ\n"), true).get(5, TimeUnit.SECONDS);

          var saved = json(inbox.next());
          assertThat(saved.get("type").asString()).isEqualTo("saved");
          assertThat(saved.get("fingerprint").get("value").asString()).isEqualTo(F2.value());
          assertThat(saved.get("by").asString()).isEqualTo(ADMIN.value().toString());
          assertThat(editing.saved).isEqualTo("# Setlist ñ\n");
          ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(5, TimeUnit.SECONDS);
        });
  }

  @Test
  void withoutASessionTheHandshakeIsRefused() {
    JavalinTest.test(
        app(),
        (server, client) ->
            assertThatThrownBy(
                    () -> builder().buildAsync(uri(server), new Inbox()).get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(
                    WebSocketHandshakeException.class,
                    e -> assertThat(e.getResponse().statusCode()).isEqualTo(401)));
  }

  @Test
  void aPageOfAnotherSiteIsRefused() {
    JavalinTest.test(
        app(),
        (server, client) ->
            assertThatThrownBy(() ->
                    builder()
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Origin", "https://evil.example")
                        .buildAsync(uri(server), new Inbox())
                        .get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(
                    WebSocketHandshakeException.class,
                    e -> assertThat(e.getResponse().statusCode()).isEqualTo(403)));
  }

  @Test
  void aFileTheUserCannotOpenClosesWith4403() {
    editing.failure = new AccessDenied("You cannot open this file");
    JavalinTest.test(
        app(),
        (server, client) -> {
          Inbox inbox = new Inbox();
          builder()
              .header("Authorization", "Bearer " + TOKEN)
              .buildAsync(uri(server), inbox)
              .get(5, TimeUnit.SECONDS);

          assertThat(inbox.next()).isEqualTo("close 4403");
        });
  }

  @Test
  void theOriginMustBeThisServer() {
    assertThat(TextEditingRelay.sameOrigin("http://localhost:8080", "localhost:8080")).isTrue();
    assertThat(TextEditingRelay.sameOrigin("http://LOCALHOST:8080", "localhost:8080")).isTrue();
    assertThat(TextEditingRelay.sameOrigin("http://localhost:9090", "localhost:8080")).isFalse();
    assertThat(TextEditingRelay.sameOrigin("https://evil.example", "localhost:8080")).isFalse();
    assertThat(TextEditingRelay.sameOrigin("not a uri", "localhost:8080")).isFalse();
    assertThat(TextEditingRelay.sameOrigin("http://localhost:8080", null)).isFalse();
  }

  @Test
  void aWrongFileIdentifierClosesWith4400() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          Inbox inbox = new Inbox();
          builder()
              .header("Authorization", "Bearer " + TOKEN)
              .buildAsync(
                  URI.create("ws://localhost:" + server.port() + "/api/v1/files/nope/edit"), inbox)
              .get(5, TimeUnit.SECONDS);

          assertThat(inbox.next()).isEqualTo("close 4400");
        });
  }
}
