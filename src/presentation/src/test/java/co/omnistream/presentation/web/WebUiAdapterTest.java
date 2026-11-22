package co.omnistream.presentation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.presentation.rest.ApiErrors;
import co.omnistream.presentation.rest.ApiJson;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

class WebUiAdapterTest {

  private static Javalin app() {
    return Javalin.create(config -> {
      config.jsonMapper(ApiJson.forJavalin());
      ApiErrors.register(config);
      new WebUiAdapter().registerRoutes(config);
    });
  }

  @Test
  void theRootAnswersThePage() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.get("/");

          assertThat(response.code()).isEqualTo(200);
          assertThat(response.headers().get("Content-Type").get(0)).startsWith("text/html");
          assertThat(response.body().string()).contains("<title>OmniStream</title>");
        });
  }

  @Test
  void theScriptTheStylesAndTheFontAreServed() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var script = client.get("/app.js");
          var styles = client.get("/style.css");

          assertThat(script.code()).isEqualTo(200);
          assertThat(script.headers().get("Content-Type").get(0)).contains("javascript");
          assertThat(script.body().string()).contains("const API = \"/api/v1\"");
          assertThat(styles.code()).isEqualTo(200);
          assertThat(styles.headers().get("Content-Type").get(0)).startsWith("text/css");
          assertThat(client.get("/fonts/bricolage-grotesque.woff2").code()).isEqualTo(200);
        });
  }

  @Test
  void aSharedLinkAnswersThePageToo() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.get("/s/abc123?collection=x");

          assertThat(response.code()).isEqualTo(200);
          assertThat(response.body().string()).contains("<title>OmniStream</title>");
        });
  }

  @Test
  void anUnknownApiRouteIsStillAJsonError() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.get("/api/v1/nothing-here");

          assertThat(response.code()).isEqualTo(404);
          assertThat(response.body().string()).contains("\"error\":\"NotFound\"");
        });
  }

  @Test
  void aMissingPageStopsTheStartup() {
    WebUiAdapter missing = new WebUiAdapter("/no-such-folder");

    assertThatThrownBy(() -> Javalin.create(missing::registerRoutes))
        .isInstanceOf(IllegalStateException.class);
  }
}
