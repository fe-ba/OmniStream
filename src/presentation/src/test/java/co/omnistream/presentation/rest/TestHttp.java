package co.omnistream.presentation.rest;

import io.javalin.testtools.Request;
import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;

/** Small helpers for requests and responses in the API tests. */
final class TestHttp {

  private TestHttp() {}

  /** Sends the known session token ({@link FakeAccessControl#TOKEN}). */
  static Consumer<Request.Builder> loggedIn() {
    return request -> request.header("Authorization", "Bearer " + FakeAccessControl.TOKEN);
  }

  static JsonNode json(String body) {
    return ApiJson.mapper().readTree(body);
  }
}
