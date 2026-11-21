package co.omnistream.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson3;
import io.javalin.testtools.JavalinTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.cfg.DateTimeFeature;

/**
 * Checks that the HTTP stack of the primary adapters works: Javalin starts, routes a request and
 * writes a record as JSON with Jackson 3, including an {@link Instant} in ISO-8601.
 */
class JavalinSetupTest {

  record Sample(String name, Instant at) {}

  @Test
  void javalinServesARecordAsJson() {
    Javalin app =
        Javalin.create(config -> {
          // Javalin's default mapper writes dates as numbers; the API writes them as ISO-8601
          config.jsonMapper(
              new JavalinJackson3()
                  .updateMapper(
                      mapper -> mapper.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)));
          config.routes.get(
              "/sample", ctx -> ctx.json(new Sample("ok", Instant.parse("2026-01-15T10:00:00Z"))));
        });

    JavalinTest.test(
        app,
        (server, client) -> {
          var response = client.get("/sample");

          assertThat(response.code()).isEqualTo(200);
          assertThat(response.body().string())
              .isEqualTo("{\"name\":\"ok\",\"at\":\"2026-01-15T10:00:00Z\"}");
        });
  }
}
