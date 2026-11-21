package co.omnistream.presentation.rest;

import static co.omnistream.presentation.rest.FakeAccessControl.ADMIN;
import static co.omnistream.presentation.rest.Samples.FILE;
import static co.omnistream.presentation.rest.Samples.FILE_ID;
import static co.omnistream.presentation.rest.TestHttp.json;
import static co.omnistream.presentation.rest.TestHttp.loggedIn;
import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.playback.SessionId;
import co.omnistream.domain.model.playback.SessionStatus;
import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.StreamProfile;
import co.omnistream.domain.port.in.IAuditTrail;
import co.omnistream.domain.port.in.ICatalogFeed;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.domain.port.in.IPlayback;
import co.omnistream.domain.port.in.ISearch;
import co.omnistream.domain.port.in.ISync;
import co.omnistream.domain.snapshot.PlaybackSessionSnapshot;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class RestApiPlaybackTest {

  private static final String API = RestApiAdapter.API;
  private static final String SESSION = "00000000-0000-4000-8000-0000000000d1";
  private static final SessionId SESSION_ID = new SessionId(UUID.fromString(SESSION));

  private final Recording<IPlayback> playback = Recording.of(IPlayback.class);

  private Javalin app() {
    RestApiAdapter adapter =
        new RestApiAdapter(
            Unused.port(ICatalogManagement.class),
            playback.port(),
            Unused.port(ISearch.class),
            new FakeAccessControl(),
            Unused.port(ISync.class),
            Unused.port(IAuditTrail.class),
            Unused.port(ICatalogFeed.class));
    return Javalin.create(adapter::registerRoutes);
  }

  private static PlaybackSessionSnapshot session() {
    return new PlaybackSessionSnapshot(
        SESSION_ID,
        FILE_ID,
        ADMIN,
        Duration.ofSeconds(90),
        Duration.ofSeconds(180),
        SessionStatus.ACTIVE,
        Instant.parse("2026-11-20T15:04:05Z"));
  }

  /** A new stream of ten bytes on every call, as a file read from disk. */
  private static MediaStream tenBytes(boolean transcoded) {
    var data = new ByteArrayInputStream("0123456789".getBytes(StandardCharsets.US_ASCII));
    return transcoded
        ? new MediaStream(data, "video/mp4", true, Optional.empty())
        : new MediaStream(data, "audio/mpeg", false, Optional.of(10L));
  }

  @Test
  void openingASessionAnswersItWithPositionsInSeconds() {
    playback.answer("openSession", session()).answer("resumeFromLastPosition", session());
    JavalinTest.test(
        app(),
        (server, client) -> {
          var opened = client.post(API + "/files/" + FILE + "/sessions", null, loggedIn());
          var resumed = client.post(API + "/files/" + FILE + "/sessions/resume", null, loggedIn());

          assertThat(opened.code()).isEqualTo(201);
          assertThat(resumed.code()).isEqualTo(201);
          JsonNode body = json(opened.body().string());
          assertThat(body.get("id").asString()).isEqualTo(SESSION);
          assertThat(body.get("position").asDouble()).isEqualTo(90.0);
          assertThat(body.get("totalDuration").asDouble()).isEqualTo(180.0);
          assertThat(playback.calls.get(0).args()).containsExactly(ADMIN, FILE_ID);
          assertThat(playback.calls.get(1).method()).isEqualTo("resumeFromLastPosition");
        });
  }

  @Test
  void aFileIsStreamedWholeWithoutARange() {
    playback.answer("requestStream", args -> tenBytes(false));
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.get(API + "/sessions/" + SESSION + "/stream", loggedIn());

          assertThat(response.code()).isEqualTo(200);
          assertThat(response.body().string()).isEqualTo("0123456789");
          assertThat(response.headers().get("Accept-Ranges")).containsExactly("bytes");
          var call = playback.onlyCall("requestStream");
          assertThat(call.args().subList(0, 2)).containsExactly(ADMIN, SESSION_ID);
          assertThat(((StreamProfile) call.args().get(2)).isDirectPlay()).isTrue();
        });
  }

  @Test
  void aRangeRequestAnswersPartOfTheFile() {
    playback.answer("requestStream", args -> tenBytes(false));
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response =
              client.get(
                  API + "/sessions/" + SESSION + "/stream",
                  request -> loggedIn().accept(request.header("Range", "bytes=2-")));

          assertThat(response.code()).isEqualTo(206);
          assertThat(response.body().string()).isEqualTo("23456789");
          assertThat(response.headers().get("Content-Range")).containsExactly("bytes 2-9/10");
        });
  }

  @Test
  void aTranscodedStreamIsSentAsItIsProduced() {
    playback.answer("requestStream", args -> tenBytes(true));
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response =
              client.get(
                  API + "/sessions/" + SESSION + "/stream?codec=h264&bitrateKbps=800", loggedIn());

          assertThat(response.code()).isEqualTo(200);
          assertThat(response.body().string()).isEqualTo("0123456789");
          assertThat(response.headers().get("Content-Type").get(0)).startsWith("video/mp4");
          assertThat(playback.onlyCall("requestStream").args().get(2))
              .isEqualTo(
                  new StreamProfile(Optional.of("h264"), Optional.of(800), Optional.empty()));
        });
  }

  @Test
  void aBitrateThatIsNotAPositiveNumberIsABadRequest() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          String stream = API + "/sessions/" + SESSION + "/stream";
          assertThat(client.get(stream + "?bitrateKbps=fast", loggedIn()).code()).isEqualTo(400);
          assertThat(client.get(stream + "?bitrateKbps=0", loggedIn()).code()).isEqualTo(400);
          assertThat(playback.calls).isEmpty();
        });
  }

  @Test
  void theProgressIsSentInSeconds() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response =
              client.put(
                  API + "/sessions/" + SESSION + "/position", "{\"position\": 754.25}", loggedIn());

          assertThat(response.code()).isEqualTo(204);
          assertThat(playback.onlyCall("recordProgress").args())
              .containsExactly(ADMIN, SESSION_ID, Duration.ofMillis(754_250));
        });
  }

  @Test
  void aNegativePositionIsABadRequest() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response =
              client.put(
                  API + "/sessions/" + SESSION + "/position", "{\"position\": -1}", loggedIn());

          assertThat(response.code()).isEqualTo(400);
          assertThat(playback.calls).isEmpty();
        });
  }

  @Test
  void aSessionIsPausedResumedAndFinished() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          String session = API + "/sessions/" + SESSION;
          assertThat(client.post(session + "/pause", null, loggedIn()).code()).isEqualTo(204);
          assertThat(client.post(session + "/resume", null, loggedIn()).code()).isEqualTo(204);
          assertThat(client.post(session + "/finish", null, loggedIn()).code()).isEqualTo(204);

          assertThat(playback.calls)
              .extracting(Recording.Call::method)
              .containsExactly("pauseSession", "resumeSession", "finishSession");
          assertThat(playback.calls)
              .allSatisfy(call -> assertThat(call.args()).containsExactly(ADMIN, SESSION_ID));
        });
  }

  @Test
  void theStreamNeedsASession() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          assertThat(client.get(API + "/sessions/" + SESSION + "/stream").code()).isEqualTo(401);
          assertThat(playback.calls).isEmpty();
        });
  }
}
