package co.omnistream.presentation.rest;

import static co.omnistream.presentation.rest.FakeAccessControl.ADMIN;
import static co.omnistream.presentation.rest.Samples.FILE;
import static co.omnistream.presentation.rest.Samples.FILE_ID;
import static co.omnistream.presentation.rest.TestHttp.json;
import static co.omnistream.presentation.rest.TestHttp.loggedIn;
import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.port.dto.OfflinePackage;
import co.omnistream.domain.port.dto.SyncDelta;
import co.omnistream.domain.port.in.IAuditTrail;
import co.omnistream.domain.port.in.ICatalogFeed;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.domain.port.in.IPlayback;
import co.omnistream.domain.port.in.ISearch;
import co.omnistream.domain.port.in.ISync;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class RestApiSyncTest {

  private static final String API = RestApiAdapter.API;
  private static final String SINCE = "2026-11-20T15:04:05Z";

  private final Recording<ISync> sync = Recording.of(ISync.class);

  private Javalin app() {
    RestApiAdapter adapter =
        new RestApiAdapter(
            Unused.port(ICatalogManagement.class),
            Unused.port(IPlayback.class),
            Unused.port(ISearch.class),
            new FakeAccessControl(),
            sync.port(),
            Unused.port(IAuditTrail.class),
            Unused.port(ICatalogFeed.class));
    return Javalin.create(adapter::registerRoutes);
  }

  @Test
  void changesAreAskedSinceAnInstant() {
    sync.answer("syncChanges", new SyncDelta(List.of(FILE_ID), List.of(), List.of()));
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.get(API + "/sync/changes?since=" + SINCE, loggedIn());

          assertThat(response.code()).isEqualTo(200);
          JsonNode delta = json(response.body().string());
          assertThat(delta.get("added").get(0).asString()).isEqualTo(FILE);
          assertThat(sync.onlyCall("syncChanges").args())
              .containsExactly(ADMIN, Instant.parse(SINCE));
        });
  }

  @Test
  void pendingChangesAreAYesOrNo() {
    sync.answer("hasPendingChanges", true);
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.get(API + "/sync/pending?since=" + SINCE, loggedIn());

          assertThat(json(response.body().string()).get("pending").asBoolean()).isTrue();
        });
  }

  @Test
  void theInstantIsRequiredAndMustBeIso8601() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          assertThat(client.get(API + "/sync/changes", loggedIn()).code()).isEqualTo(400);
          assertThat(client.get(API + "/sync/changes?since=yesterday", loggedIn()).code())
              .isEqualTo(400);
          assertThat(sync.calls).isEmpty();
        });
  }

  @Test
  void anOfflineCopyIsDownloadedAsAnAttachment(@TempDir Path folder) throws Exception {
    Path content = Files.writeString(folder.resolve("song.mp3"), "DATA");
    sync.answer("cacheContent", new OfflinePackage(FILE_ID, List.of(), content));
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.get(API + "/files/" + FILE + "/offline", loggedIn());

          assertThat(response.code()).isEqualTo(200);
          assertThat(response.body().string()).isEqualTo("DATA");
          assertThat(response.headers().get("Content-Disposition"))
              .containsExactly("attachment; filename=\"song.mp3\"");
          assertThat(sync.onlyCall("cacheContent").args()).containsExactly(ADMIN, FILE_ID);
        });
  }
}
