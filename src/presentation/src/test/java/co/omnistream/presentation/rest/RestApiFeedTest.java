package co.omnistream.presentation.rest;

import static co.omnistream.presentation.rest.FakeAccessControl.ADMIN;
import static co.omnistream.presentation.rest.FakeAccessControl.TOKEN;
import static co.omnistream.presentation.rest.TestHttp.json;
import static co.omnistream.presentation.rest.TestHttp.loggedIn;
import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.event.ChangeType;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.CatalogChange;
import co.omnistream.domain.port.in.CatalogChangeListener;
import co.omnistream.domain.port.in.FeedSubscription;
import co.omnistream.domain.port.in.IAuditTrail;
import co.omnistream.domain.port.in.ICatalogFeed;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.domain.port.in.IPlayback;
import co.omnistream.domain.port.in.ISearch;
import co.omnistream.domain.port.in.ISync;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RestApiFeedTest {

  private static final String API = RestApiAdapter.API;
  private static final ItemId FILE =
      new ItemId(UUID.fromString("00000000-0000-4000-8000-0000000000f1"));
  private static final Instant AT = Instant.parse("2026-11-21T17:00:00Z");

  /** A feed with one follower at most, which the test can push changes to. */
  private static final class FakeFeed implements ICatalogFeed {
    volatile UserId follower;
    volatile CatalogChangeListener listener;
    volatile RuntimeException failure;

    @Override
    public FeedSubscription follow(UserId u, CatalogChangeListener listener) {
      if (failure != null) {
        throw failure;
      }
      this.follower = u;
      this.listener = listener;
      return () -> {};
    }
  }

  private final FakeFeed feed = new FakeFeed();

  private Javalin app() {
    RestApiAdapter adapter =
        new RestApiAdapter(
            Unused.port(ICatalogManagement.class),
            Unused.port(IPlayback.class),
            Unused.port(ISearch.class),
            new FakeAccessControl(),
            Unused.port(ISync.class),
            Unused.port(IAuditTrail.class),
            feed);
    return Javalin.create(adapter::registerRoutes);
  }

  /** The next line of the stream, or a failure if none comes within five seconds. */
  private static String next(Iterator<String> lines) throws Exception {
    return CompletableFuture.supplyAsync(lines::next).get(5, TimeUnit.SECONDS);
  }

  @Test
  void theStreamSaysReadyAndThenEachChange() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          HttpRequest request =
              HttpRequest.newBuilder(URI.create(client.getOrigin() + API + "/events"))
                  .header("Accept", RestApiAdapter.EVENT_STREAM)
                  .header("Authorization", "Bearer " + TOKEN)
                  .timeout(Duration.ofSeconds(5))
                  .build();
          HttpResponse<Stream<String>> response =
              HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofLines());
          try (Stream<String> body = response.body()) {
            Iterator<String> lines = body.iterator();

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith(RestApiAdapter.EVENT_STREAM);
            assertThat(next(lines)).isEqualTo("event: ready");
            assertThat(next(lines)).isEqualTo("data: {}");
            assertThat(next(lines)).isEqualTo("");
            assertThat(feed.follower).isEqualTo(ADMIN);

            feed.listener.onChange(
                new CatalogChange(FILE, ChangeType.MODIFIED, "FileModified", AT));

            assertThat(next(lines)).isEqualTo("event: change");
            String data = next(lines);
            assertThat(data).startsWith("data: ");
            var change = json(data.substring("data: ".length()));
            assertThat(change.get("item").asString()).isEqualTo(FILE.value().toString());
            assertThat(change.get("change").asString()).isEqualTo("MODIFIED");
            assertThat(change.get("event").asString()).isEqualTo("FileModified");
            assertThat(change.get("at").asString()).isEqualTo("2026-11-21T17:00:00Z");
          }
        });
  }

  @Test
  void withoutAskingForAStreamItIsABadRequest() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.get(API + "/events", loggedIn());

          assertThat(response.code()).isEqualTo(400);
          assertThat(feed.follower).isNull();
        });
  }

  @Test
  void withoutASessionItIsUnauthorized() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response =
              client.get(
                  API + "/events",
                  request -> request.header("Accept", RestApiAdapter.EVENT_STREAM));

          assertThat(response.code()).isEqualTo(401);
          assertThat(feed.follower).isNull();
        });
  }

  @Test
  void anErrorWhenFollowingStillAnswersWithItsStatus() {
    feed.failure = new ResourceNotFound("No active user");
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response =
              client.get(
                  API + "/events",
                  request ->
                      request
                          .header("Accept", RestApiAdapter.EVENT_STREAM)
                          .header("Authorization", "Bearer " + TOKEN));

          assertThat(response.code()).isEqualTo(404);
        });
  }
}
