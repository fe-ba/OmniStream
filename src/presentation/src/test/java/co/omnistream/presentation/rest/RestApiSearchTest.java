package co.omnistream.presentation.rest;

import static co.omnistream.presentation.rest.FakeAccessControl.ADMIN;
import static co.omnistream.presentation.rest.Samples.COLLECTION;
import static co.omnistream.presentation.rest.Samples.COLLECTION_ID;
import static co.omnistream.presentation.rest.Samples.FILE;
import static co.omnistream.presentation.rest.Samples.FILE_ID;
import static co.omnistream.presentation.rest.TestHttp.json;
import static co.omnistream.presentation.rest.TestHttp.loggedIn;
import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.model.catalog.TagSource;
import co.omnistream.domain.port.dto.CollectionCommand;
import co.omnistream.domain.port.dto.SearchCriteria;
import co.omnistream.domain.port.in.IAuditTrail;
import co.omnistream.domain.port.in.ICatalogFeed;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.domain.port.in.IPlayback;
import co.omnistream.domain.port.in.ISearch;
import co.omnistream.domain.port.in.ISync;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class RestApiSearchTest {

  private static final String API = RestApiAdapter.API;

  private final Recording<ISearch> search = Recording.of(ISearch.class);

  private Javalin app() {
    RestApiAdapter adapter =
        new RestApiAdapter(
            Unused.port(ICatalogManagement.class),
            Unused.port(IPlayback.class),
            search.port(),
            new FakeAccessControl(),
            Unused.port(ISync.class),
            Unused.port(IAuditTrail.class),
            Unused.port(ICatalogFeed.class));
    return Javalin.create(adapter::registerRoutes);
  }

  @Test
  void aSearchAnswersFilesAndCollectionsTellingThemApart() {
    search.answer("searchContent", List.of(Samples.file(), Samples.collection()));
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.get(API + "/search?q=jazz", loggedIn());

          assertThat(response.code()).isEqualTo(200);
          JsonNode items = json(response.body().string());
          assertThat(items.get(0).get("kind").asString()).isEqualTo("file");
          assertThat(items.get(1).get("kind").asString()).isEqualTo("collection");
          assertThat(search.onlyCall("searchContent").args()).containsExactly(ADMIN, "jazz");
        });
  }

  @Test
  void aSearchNeedsText() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          assertThat(client.get(API + "/search", loggedIn()).code()).isEqualTo(400);
          assertThat(search.calls).isEmpty();
        });
  }

  @Test
  void filtersBecomeSearchCriteria() {
    search.answer("filterByCriteria", List.of(Samples.file()));
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.get(API + "/files?type=AUDIO&tag=jazz&tag=live", loggedIn());

          assertThat(response.code()).isEqualTo(200);
          assertThat(search.onlyCall("filterByCriteria").args().get(1))
              .isEqualTo(
                  new SearchCriteria(
                      Optional.empty(),
                      Optional.of(MediaType.AUDIO),
                      Set.of(
                          new Tag("jazz", TagSource.MANUAL), new Tag("live", TagSource.MANUAL))));
        });
  }

  @Test
  void withoutParametersTheCriteriaHaveNoFilters() {
    search.answer("filterByCriteria", List.of());
    JavalinTest.test(
        app(),
        (server, client) -> {
          assertThat(client.get(API + "/files", loggedIn()).code()).isEqualTo(200);
          SearchCriteria criteria =
              (SearchCriteria) search.onlyCall("filterByCriteria").args().get(1);
          assertThat(criteria.hasFilters()).isFalse();
        });
  }

  @Test
  void anUnknownTypeIsABadRequest() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          assertThat(client.get(API + "/files?type=HOLOGRAM", loggedIn()).code()).isEqualTo(400);
          assertThat(search.calls).isEmpty();
        });
  }

  @Test
  void theLibraryIsBrowsedFromTheTopOrFromACollection() {
    search.answer("browseLibrary", List.of(Samples.collection()));
    JavalinTest.test(
        app(),
        (server, client) -> {
          assertThat(client.get(API + "/library", loggedIn()).code()).isEqualTo(200);
          assertThat(client.get(API + "/collections/" + COLLECTION + "/items", loggedIn()).code())
              .isEqualTo(200);

          assertThat(search.calls.get(0).args()).containsExactly(ADMIN);
          assertThat(search.calls.get(1).args()).containsExactly(ADMIN, COLLECTION_ID);
        });
  }

  @Test
  void creatingACollectionSendsACommandWithoutIdentifier() {
    search.answer("manageCollections", Samples.collection());
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response =
              client.post(
                  API + "/collections",
                  "{\"name\": \"Favorites\", \"itemsToAdd\": [\"" + FILE + "\"]}",
                  loggedIn());

          assertThat(response.code()).isEqualTo(201);
          assertThat(json(response.body().string()).get("kind").asString()).isEqualTo("collection");
          assertThat(search.onlyCall("manageCollections").args().get(1))
              .isEqualTo(
                  new CollectionCommand(
                      Optional.empty(), Optional.of("Favorites"), List.of(FILE_ID)));
        });
  }

  @Test
  void aCollectionWithoutNameIsABadRequest() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          assertThat(client.post(API + "/collections", "{}", loggedIn()).code()).isEqualTo(400);
          assertThat(search.calls).isEmpty();
        });
  }

  @Test
  void changingACollectionSendsItsIdentifier() {
    search.answer("manageCollections", Samples.collection());
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response =
              client.patch(
                  API + "/collections/" + COLLECTION,
                  "{\"itemsToAdd\": [\"" + FILE + "\"]}",
                  loggedIn());

          assertThat(response.code()).isEqualTo(200);
          assertThat(search.onlyCall("manageCollections").args().get(1))
              .isEqualTo(
                  new CollectionCommand(
                      Optional.of(COLLECTION_ID), Optional.empty(), List.of(FILE_ID)));
        });
  }

  @Test
  void aTagFromTheApiIsManual() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response =
              client.post(API + "/files/" + FILE + "/tags", "{\"name\": \"jazz\"}", loggedIn());

          assertThat(response.code()).isEqualTo(204);
          assertThat(search.onlyCall("tagFile").args())
              .containsExactly(ADMIN, FILE_ID, new Tag("jazz", TagSource.MANUAL));
        });
  }
}
