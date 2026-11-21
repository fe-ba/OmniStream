package co.omnistream.presentation.rest;

import static co.omnistream.presentation.rest.FakeAccessControl.ADMIN;
import static co.omnistream.presentation.rest.Samples.FILE;
import static co.omnistream.presentation.rest.Samples.FILE_ID;
import static co.omnistream.presentation.rest.TestHttp.json;
import static co.omnistream.presentation.rest.TestHttp.loggedIn;
import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.catalog.DuplicateAction;
import co.omnistream.domain.model.catalog.IngestionResult;
import co.omnistream.domain.port.dto.ServerStatus;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.domain.port.in.IPlayback;
import co.omnistream.domain.port.in.ISearch;
import co.omnistream.domain.port.in.ISync;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class RestApiCatalogTest {

  private static final String API = RestApiAdapter.API;

  private final Recording<ICatalogManagement> catalog = Recording.of(ICatalogManagement.class);

  private Javalin app() {
    RestApiAdapter adapter =
        new RestApiAdapter(
            catalog.port(),
            Unused.port(IPlayback.class),
            Unused.port(ISearch.class),
            new FakeAccessControl(),
            Unused.port(ISync.class));
    return Javalin.create(adapter::registerRoutes);
  }

  private static String multipart(String fileName, String content) {
    return "--b\r\n"
        + "Content-Disposition: form-data; name=\"file\"; filename=\""
        + fileName
        + "\"\r\n"
        + "Content-Type: audio/mpeg\r\n\r\n"
        + content
        + "\r\n--b--\r\n";
  }

  private static String read(InputStream data) {
    try {
      return new String(data.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Test
  void anUploadedFileReachesThePortWithItsNameAndContent() {
    String[] received = new String[1];
    catalog.answer(
        "uploadFile",
        args -> {
          received[0] = args[1] + ":" + read((InputStream) args[2]);
          return new IngestionResult(FILE_ID, false, Optional.empty());
        });
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response =
              client.request(
                  API + "/files",
                  request ->
                      loggedIn()
                          .accept(
                              request
                                  .post(BodyPublishers.ofString(multipart("song.mp3", "DATA")))
                                  .header("Content-Type", "multipart/form-data; boundary=b")));

          assertThat(response.code()).isEqualTo(201);
          assertThat(received[0]).isEqualTo("song.mp3:DATA");
          JsonNode result = json(response.body().string());
          assertThat(result.get("fileId").asString()).isEqualTo(FILE);
          assertThat(result.get("duplicate").asBoolean()).isFalse();
          assertThat(catalog.onlyCall("uploadFile").args().get(0)).isEqualTo(ADMIN);
        });
  }

  @Test
  void aRejectedDuplicateAnswers200() {
    catalog.answer(
        "uploadFile", new IngestionResult(FILE_ID, true, Optional.of(DuplicateAction.REJECT)));
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response =
              client.request(
                  API + "/files",
                  request ->
                      loggedIn()
                          .accept(
                              request
                                  .post(BodyPublishers.ofString(multipart("song.mp3", "DATA")))
                                  .header("Content-Type", "multipart/form-data; boundary=b")));

          assertThat(response.code()).isEqualTo(200);
          assertThat(json(response.body().string()).get("action").asString()).isEqualTo("REJECT");
        });
  }

  @Test
  void anUploadWithoutTheFileFieldIsABadRequest() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          assertThat(client.post(API + "/files", "{}", loggedIn()).code()).isEqualTo(400);
          assertThat(catalog.calls).isEmpty();
        });
  }

  @Test
  void filesCanBeListedEditedAndDeleted() {
    catalog.answer("detectDuplicates", List.of(Samples.file()));
    JavalinTest.test(
        app(),
        (server, client) -> {
          var duplicates = client.get(API + "/files/duplicates", loggedIn());
          assertThat(duplicates.code()).isEqualTo(200);
          assertThat(json(duplicates.body().string()).get(0).get("kind").asString())
              .isEqualTo("file");

          assertThat(
                  client
                      .put(
                          API + "/files/" + FILE + "/metadata/artist",
                          "{\"value\": \"Ana\"}",
                          loggedIn())
                      .code())
              .isEqualTo(204);
          assertThat(client.delete(API + "/files/" + FILE, null, loggedIn()).code()).isEqualTo(204);

          assertThat(catalog.calls)
              .extracting(Recording.Call::method)
              .containsExactly("detectDuplicates", "editMetadata", "deleteFile");
          assertThat(catalog.calls.get(1).args()).containsExactly(ADMIN, FILE_ID, "artist", "Ana");
          assertThat(catalog.calls.get(2).args()).containsExactly(ADMIN, FILE_ID);
        });
  }

  @Test
  void administrationRoutesPassPathsAndAnswerResults() {
    catalog
        .answer("reindexLibrary", 42)
        .answer("backupCatalog", Path.of("/backups/catalog.db"))
        .answer("monitorServerStatus", new ServerStatus(42, 1_000_000, 2));
    JavalinTest.test(
        app(),
        (server, client) -> {
          var reindex = client.post(API + "/admin/reindex", "{\"root\": \"/library\"}", loggedIn());
          assertThat(json(reindex.body().string()).get("indexed").asInt()).isEqualTo(42);

          var backup =
              client.post(API + "/admin/backup", "{\"destination\": \"/backups\"}", loggedIn());
          assertThat(json(backup.body().string()).get("path").asString())
              .isEqualTo("/backups/catalog.db");

          assertThat(
                  client
                      .put(API + "/admin/library-path", "{\"path\": \"/media\"}", loggedIn())
                      .code())
              .isEqualTo(204);

          var status = json(client.get(API + "/admin/status", loggedIn()).body().string());
          assertThat(status.get("indexedFiles").asInt()).isEqualTo(42);
          assertThat(status.get("activeSessions").asInt()).isEqualTo(2);

          assertThat(catalog.calls.get(0).args()).containsExactly(ADMIN, Path.of("/library"));
          assertThat(catalog.calls.get(1).args()).containsExactly(ADMIN, Path.of("/backups"));
          assertThat(catalog.calls.get(2).args()).containsExactly(ADMIN, Path.of("/media"));
        });
  }

  @Test
  void aBodyWithoutItsRequiredFieldIsABadRequest() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          assertThat(client.post(API + "/admin/reindex", "{}", loggedIn()).code()).isEqualTo(400);
          assertThat(
                  client.put(API + "/files/" + FILE + "/metadata/artist", "{}", loggedIn()).code())
              .isEqualTo(400);
          assertThat(catalog.calls).isEmpty();
        });
  }

  @Test
  void everyCatalogRouteNeedsASession() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          assertThat(client.get(API + "/files/duplicates").code()).isEqualTo(401);
          assertThat(client.delete(API + "/files/" + FILE).code()).isEqualTo(401);
          assertThat(client.get(API + "/admin/status").code()).isEqualTo(401);
          assertThat(catalog.calls).isEmpty();
        });
  }
}
