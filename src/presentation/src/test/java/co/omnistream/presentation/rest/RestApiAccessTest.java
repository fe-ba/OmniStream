package co.omnistream.presentation.rest;

import static co.omnistream.presentation.rest.FakeAccessControl.ADMIN;
import static co.omnistream.presentation.rest.FakeAccessControl.CREATED;
import static co.omnistream.presentation.rest.FakeAccessControl.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.port.in.IAuditTrail;
import co.omnistream.domain.port.in.ICatalogFeed;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.domain.port.in.IPlayback;
import co.omnistream.domain.port.in.ISearch;
import co.omnistream.domain.port.in.ISync;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Request;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class RestApiAccessTest {

  private static final String API = RestApiAdapter.API;
  private static final String ITEM = "00000000-0000-4000-8000-0000000000f1";
  private static final String USER = "00000000-0000-4000-8000-0000000000b2";

  private final FakeAccessControl access = new FakeAccessControl();

  private Javalin app() {
    RestApiAdapter adapter =
        new RestApiAdapter(
            Unused.port(ICatalogManagement.class),
            Unused.port(IPlayback.class),
            Unused.port(ISearch.class),
            access,
            Unused.port(ISync.class),
            Unused.port(IAuditTrail.class),
            Unused.port(ICatalogFeed.class));
    return Javalin.create(adapter::registerRoutes);
  }

  private static Consumer<Request.Builder> bearer(String token) {
    return request -> request.header("Authorization", "Bearer " + token);
  }

  private static JsonNode json(String body) {
    return ApiJson.mapper().readTree(body);
  }

  // ---------------------------------------------------------------- sessions

  @Test
  void loginAnswersTheTokenAndSetsAnHttpOnlyCookie() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response =
              client.post(API + "/auth/login", "{\"username\": \"ana\", \"password\": \"secret\"}");

          assertThat(response.code()).isEqualTo(200);
          JsonNode token = json(response.body().string());
          assertThat(token.get("value").asString()).isEqualTo(TOKEN);
          assertThat(token.get("userId").asString()).isEqualTo(ADMIN.value().toString());
          assertThat(token.get("role").asString()).isEqualTo("ADMIN");
          assertThat(token.get("expiresAt").asString()).isEqualTo("2026-11-21T03:00:00Z");
          List<String> cookies = response.headers().get("Set-Cookie");
          assertThat(cookies).isNotNull();
          assertThat(cookies.get(0))
              .startsWith(RestApiAdapter.TOKEN_COOKIE + "=" + TOKEN)
              .containsIgnoringCase("HttpOnly")
              .contains("SameSite=Strict");
          assertThat(access.calls).containsExactly("authenticateUser local ana");
        });
  }

  @Test
  void wrongCredentialsAreUnauthorized() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response =
              client.post(API + "/auth/login", "{\"username\": \"ana\", \"password\": \"nope\"}");

          assertThat(response.code()).isEqualTo(401);
          assertThat(json(response.body().string()).get("error").asString())
              .isEqualTo("InvalidCredentials");
        });
  }

  @Test
  void aLoginWithoutPasswordIsABadRequest() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.post(API + "/auth/login", "{\"username\": \"ana\"}");

          assertThat(response.code()).isEqualTo(400);
          assertThat(access.calls).isEmpty();
        });
  }

  @Test
  void aProtectedRouteNeedsAValidToken() {
    String path = API + "/users/" + USER + "/deactivate";
    JavalinTest.test(
        app(),
        (server, client) -> {
          assertThat(client.post(path).code()).isEqualTo(401);
          assertThat(client.post(path, null, bearer("forged")).code()).isEqualTo(401);
          assertThat(access.calls).isEmpty();
        });
  }

  @Test
  void theTokenCanComeInTheHeaderOrInTheCookie() {
    String path = API + "/users/" + USER + "/deactivate";
    JavalinTest.test(
        app(),
        (server, client) -> {
          assertThat(client.post(path, null, bearer(TOKEN)).code()).isEqualTo(204);
          assertThat(
                  client
                      .post(
                          path,
                          null,
                          request ->
                              request.header("Cookie", RestApiAdapter.TOKEN_COOKIE + "=" + TOKEN))
                      .code())
              .isEqualTo(204);
          assertThat(access.calls)
              .containsExactly(
                  "deactivateUser " + ADMIN.value() + " " + USER,
                  "deactivateUser " + ADMIN.value() + " " + USER);
        });
  }

  @Test
  void logoutEndsTheSessionAndClearsTheCookie() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.post(API + "/auth/logout", null, bearer(TOKEN));

          assertThat(response.code()).isEqualTo(204);
          assertThat(access.calls).containsExactly("logout " + TOKEN);
          assertThat(response.headers().get("Set-Cookie").get(0))
              // an empty value that has already expired: Jetty writes "Expires=...1970"
              .startsWith(RestApiAdapter.TOKEN_COOKIE + "=;")
              .containsAnyOf("Max-Age=0", "01 Jan 1970");
        });
  }

  @Test
  void aSharedLinkIsOpenedWithoutLoggingIn() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.post(API + "/links/abc123/access");

          assertThat(response.code()).isEqualTo(200);
          assertThat(json(response.body().string()).get("value").asString()).isEqualTo(TOKEN);
          assertThat(response.headers().get("Set-Cookie")).isNotNull();
          assertThat(access.calls).containsExactly("accessWithLink abc123");
        });
  }

  // ---------------------------------------------------------------- sharing and users

  @Test
  void sharingACollectionAnswersTheNewLink() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response =
              client.post(
                  API + "/collections/" + ITEM + "/links",
                  "{\"level\": \"READ\", \"lifetime\": 604800}",
                  bearer(TOKEN));

          assertThat(response.code()).isEqualTo(201);
          JsonNode link = json(response.body().string());
          assertThat(link.get("token").asString()).isEqualTo("link-token");
          assertThat(link.get("collectionId").asString()).isEqualTo(ITEM);
          assertThat(access.calls)
              .containsExactly("shareCollection " + ADMIN.value() + " " + ITEM + " READ PT168H");
        });
  }

  @Test
  void anAdministratorCreatesAUser() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response =
              client.post(
                  API + "/users",
                  "{\"username\": \"luis\", \"password\": \"pw\", \"role\": \"USER\"}",
                  bearer(TOKEN));

          assertThat(response.code()).isEqualTo(201);
          assertThat(json(response.body().string()).get("id").asString())
              .isEqualTo(CREATED.value().toString());
          assertThat(access.calls).containsExactly("createUser " + ADMIN.value() + " luis USER");
        });
  }

  @Test
  void rolesAndPermissionsAreManagedPerUser() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          assertThat(
                  client
                      .put(API + "/users/" + USER + "/role", "{\"role\": \"ADMIN\"}", bearer(TOKEN))
                      .code())
              .isEqualTo(204);
          assertThat(
                  client
                      .post(
                          API + "/users/" + USER + "/permissions",
                          "{\"resource\": \"" + ITEM + "\", \"level\": \"WRITE\"}",
                          bearer(TOKEN))
                      .code())
              .isEqualTo(204);
          assertThat(
                  client
                      .delete(
                          API + "/users/" + USER + "/permissions/" + ITEM + "?level=WRITE",
                          null,
                          bearer(TOKEN))
                      .code())
              .isEqualTo(204);
          assertThat(access.calls)
              .containsExactly(
                  "manageRoles " + ADMIN.value() + " " + USER + " ADMIN",
                  "grantPermission " + USER + " " + ITEM + " WRITE",
                  "revokePermission " + USER + " " + ITEM + " WRITE");
        });
  }

  @Test
  void anAdministratorListsTheUsersWithTheirPermissions() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.get(API + "/users", bearer(TOKEN));

          assertThat(response.code()).isEqualTo(200);
          JsonNode user = json(response.body().string()).get(0);
          assertThat(user.get("id").asString()).isEqualTo(CREATED.value().toString());
          assertThat(user.get("username").asString()).isEqualTo("luis");
          assertThat(user.get("role").asString()).isEqualTo("USER");
          assertThat(user.get("active").asBoolean()).isTrue();
          JsonNode permission = user.get("permissions").get(0);
          assertThat(permission.get("resource").asString()).isEqualTo(ITEM);
          assertThat(permission.get("level").asString()).isEqualTo("WRITE");
          assertThat(access.calls).containsExactly("listUsers " + ADMIN.value());
        });
  }

  @Test
  void aUserIsFoundByItsUsername() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var found = client.get(API + "/users/by-name/luis", bearer(TOKEN));
          var missing = client.get(API + "/users/by-name/nadie", bearer(TOKEN));

          assertThat(found.code()).isEqualTo(200);
          assertThat(json(found.body().string()).get("id").asString())
              .isEqualTo(CREATED.value().toString());
          assertThat(missing.code()).isEqualTo(404);
        });
  }

  @Test
  void theAccessListOfAnItemNamesEachUserAndLevel() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.get(API + "/items/" + ITEM + "/access", bearer(TOKEN));

          assertThat(response.code()).isEqualTo(200);
          JsonNode grant = json(response.body().string()).get(0);
          assertThat(grant.get("user").asString()).isEqualTo(CREATED.value().toString());
          assertThat(grant.get("username").asString()).isEqualTo("luis");
          assertThat(grant.get("level").asString()).isEqualTo("WRITE");
          assertThat(access.calls).containsExactly("accessTo " + ADMIN.value() + " " + ITEM);
        });
  }

  @Test
  void revokingNeedsTheLevel() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response =
              client.delete(API + "/users/" + USER + "/permissions/" + ITEM, null, bearer(TOKEN));

          assertThat(response.code()).isEqualTo(400);
          assertThat(access.calls).isEmpty();
        });
  }

  @Test
  void aMalformedIdentifierInThePathIsABadRequest() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.post(API + "/users/not-a-uuid/deactivate", null, bearer(TOKEN));

          assertThat(response.code()).isEqualTo(400);
          assertThat(json(response.body().string()).get("error").asString())
              .isEqualTo("BadRequest");
        });
  }
}
