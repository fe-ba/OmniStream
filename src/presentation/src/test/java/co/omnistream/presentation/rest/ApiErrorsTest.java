package co.omnistream.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.CyclicCollection;
import co.omnistream.domain.exception.DomainException;
import co.omnistream.domain.exception.ExpiredLink;
import co.omnistream.domain.exception.FileRejected;
import co.omnistream.domain.exception.InvalidCredentials;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.exception.VersionConflict;
import co.omnistream.domain.model.access.Permission;
import io.javalin.Javalin;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.JavalinTest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ApiErrorsTest {

  /** A domain exception that the contract does not know. */
  private static final class UnknownDomainException extends DomainException {
    private static final long serialVersionUID = 1L;

    UnknownDomainException() {
      super("new rule");
    }
  }

  /** Route path, exception it throws. */
  private static final Map<String, Supplier<Exception>> THROWERS = new LinkedHashMap<>();

  static {
    THROWERS.put("/invalid-credentials", () -> new InvalidCredentials("bad password"));
    THROWERS.put("/access-denied", () -> new AccessDenied("not yours"));
    THROWERS.put("/not-found", () -> new ResourceNotFound("no such file"));
    THROWERS.put("/version-conflict", () -> new VersionConflict("stale"));
    THROWERS.put("/cyclic", () -> new CyclicCollection("cycle"));
    THROWERS.put("/expired", () -> new ExpiredLink("expired"));
    THROWERS.put("/rejected", () -> new FileRejected("too big"));
    THROWERS.put("/illegal-argument", () -> new IllegalArgumentException("bad value"));
    THROWERS.put("/illegal-state", () -> new IllegalStateException("finished"));
    THROWERS.put("/unauthenticated", () -> new UnauthorizedResponse("no token"));
    THROWERS.put("/unknown-domain", UnknownDomainException::new);
    THROWERS.put("/bug", () -> new NullPointerException("secret detail"));
  }

  /** A server with the API's JSON and errors, and routes that throw. */
  private static Javalin app() {
    return Javalin.create(config -> {
      config.jsonMapper(ApiJson.forJavalin());
      ApiErrors.register(config);
      THROWERS.forEach((path, exception) ->
          config.routes.get(
              path,
              ctx -> {
                throw exception.get();
              }));
      config.routes.post("/permissions", ctx -> ctx.json(ctx.bodyAsClass(Permission.class)));
    });
  }

  private static void expect(
      HttpClient client, String path, int status, String error, String message) {
    var response = client.get(path);
    JsonNode body = ApiJson.mapper().readTree(response.body().string());

    assertThat(response.code()).as(path).isEqualTo(status);
    assertThat(body.get("error").asString()).as(path).isEqualTo(error);
    assertThat(body.get("message").asString()).as(path).isEqualTo(message);
  }

  @Test
  void domainExceptionsGetTheirStatusAndName() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          expect(client, "/invalid-credentials", 401, "InvalidCredentials", "bad password");
          expect(client, "/access-denied", 403, "AccessDenied", "not yours");
          expect(client, "/not-found", 404, "ResourceNotFound", "no such file");
          expect(client, "/version-conflict", 409, "VersionConflict", "stale");
          expect(client, "/cyclic", 409, "CyclicCollection", "cycle");
          expect(client, "/expired", 410, "ExpiredLink", "expired");
          expect(client, "/rejected", 422, "FileRejected", "too big");
        });
  }

  @Test
  void misuseOfTheDomainIsABadRequestOrAConflict() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          expect(client, "/illegal-argument", 400, "BadRequest", "bad value");
          expect(client, "/illegal-state", 409, "Conflict", "finished");
        });
  }

  @Test
  void aMissingTokenIsUnauthenticated() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          expect(client, "/unauthenticated", 401, "Unauthenticated", "no token");
        });
  }

  @Test
  void anUnknownRouteIsNotFound() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.get("/nothing-here");
          JsonNode body = ApiJson.mapper().readTree(response.body().string());

          assertThat(response.code()).isEqualTo(404);
          assertThat(body.get("error").asString()).isEqualTo("NotFound");
        });
  }

  @Test
  void unexpectedErrorsHideTheirDetails() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          expect(client, "/bug", 500, "InternalError", "Unexpected error");
          expect(client, "/unknown-domain", 500, "InternalError", "Unexpected error");
        });
  }

  @Test
  void aMalformedBodyIsABadRequest() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.post("/permissions", "{\"resource\": \"not-a-uuid\"}");
          JsonNode body = ApiJson.mapper().readTree(response.body().string());

          assertThat(response.code()).isEqualTo(400);
          assertThat(body.get("error").asString()).isEqualTo("BadRequest");
        });
  }
}
