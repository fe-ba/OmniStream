package co.omnistream.presentation.rest;

import static co.omnistream.presentation.rest.FakeAccessControl.ADMIN;
import static co.omnistream.presentation.rest.TestHttp.json;
import static co.omnistream.presentation.rest.TestHttp.loggedIn;
import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.AuditQuery;
import co.omnistream.domain.port.in.IAuditTrail;
import co.omnistream.domain.port.in.ICatalogFeed;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.domain.port.in.IPlayback;
import co.omnistream.domain.port.in.ISearch;
import co.omnistream.domain.port.in.ISync;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class RestApiAuditTest {

  private static final String API = RestApiAdapter.API;
  private static final String FILE = "00000000-0000-4000-8000-0000000000f1";
  private static final String LUIS = "00000000-0000-4000-8000-0000000000b2";
  private static final Instant AT = Instant.parse("2026-11-21T16:00:00Z");

  private final Recording<IAuditTrail> audit = Recording.of(IAuditTrail.class);

  private Javalin app() {
    RestApiAdapter adapter =
        new RestApiAdapter(
            Unused.port(ICatalogManagement.class),
            Unused.port(IPlayback.class),
            Unused.port(ISearch.class),
            new FakeAccessControl(),
            Unused.port(ISync.class),
            audit.port(),
            Unused.port(ICatalogFeed.class));
    return Javalin.create(adapter::registerRoutes);
  }

  private AuditQuery askedQuery() {
    return (AuditQuery) audit.onlyCall("history").args().get(1);
  }

  @Test
  void withoutFiltersTheLatestHundredEntriesAreAsked() {
    audit.answer("history", List.of());
    JavalinTest.test(
        app(),
        (server, client) -> {
          var response = client.get(API + "/audit", loggedIn());

          assertThat(response.code()).isEqualTo(200);
          assertThat(audit.onlyCall("history").args().get(0)).isEqualTo(ADMIN);
          assertThat(askedQuery()).isEqualTo(AuditQuery.latest(100));
        });
  }

  @Test
  void everyFilterComesFromTheQueryString() {
    audit.answer("history", List.of());
    JavalinTest.test(
        app(),
        (server, client) -> {
          String query =
              "?actor="
                  + LUIS
                  + "&resource="
                  + FILE
                  + "&subject="
                  + LUIS
                  + "&action=FILE_TAGGED&action=FILE_DELETED"
                  + "&from=2026-11-01T00:00:00Z&to=2026-11-22T00:00:00Z&limit=20";

          assertThat(client.get(API + "/audit" + query, loggedIn()).code()).isEqualTo(200);

          AuditQuery asked = askedQuery();
          UserId luis = new UserId(UUID.fromString(LUIS));
          assertThat(asked.actor()).contains(luis);
          assertThat(asked.resource()).contains(new ItemId(UUID.fromString(FILE)));
          assertThat(asked.subject()).contains(luis);
          assertThat(asked.actions())
              .containsExactlyInAnyOrder(AuditAction.FILE_TAGGED, AuditAction.FILE_DELETED);
          assertThat(asked.from()).contains(Instant.parse("2026-11-01T00:00:00Z"));
          assertThat(asked.to()).contains(Instant.parse("2026-11-22T00:00:00Z"));
          assertThat(asked.limit()).isEqualTo(20);
        });
  }

  @Test
  void anEntryIsJsonWithItsOptionalPartsAsNull() {
    UserId luis = new UserId(UUID.fromString(LUIS));
    ItemId file = new ItemId(UUID.fromString(FILE));
    AuditEntry granted =
        AuditEntry.of(ADMIN, AuditAction.PERMISSION_GRANTED, AT)
            .about(file)
            .concerning(luis)
            .saying("WRITE");
    audit.answer("history", List.of(granted, AuditEntry.of(luis, AuditAction.LOGGED_IN, AT)));
    JavalinTest.test(
        app(),
        (server, client) -> {
          JsonNode entries = json(client.get(API + "/audit", loggedIn()).body().string());

          JsonNode grant = entries.get(0);
          assertThat(grant.get("id").asString()).isEqualTo(granted.id().value().toString());
          assertThat(grant.get("at").asString()).isEqualTo("2026-11-21T16:00:00Z");
          assertThat(grant.get("actor").asString()).isEqualTo(ADMIN.value().toString());
          assertThat(grant.get("action").asString()).isEqualTo("PERMISSION_GRANTED");
          assertThat(grant.get("resource").asString()).isEqualTo(FILE);
          assertThat(grant.get("subject").asString()).isEqualTo(LUIS);
          assertThat(grant.get("detail").asString()).isEqualTo("WRITE");
          JsonNode login = entries.get(1);
          assertThat(login.get("resource").isNull()).isTrue();
          assertThat(login.get("subject").isNull()).isTrue();
        });
  }

  @Test
  void aBadFilterIsABadRequest() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          for (String query :
              Set.of(
                  "?action=NOT_AN_ACTION",
                  "?limit=0",
                  "?limit=many",
                  "?resource=not-a-uuid",
                  "?from=yesterday",
                  "?from=2026-11-22T00:00:00Z&to=2026-11-01T00:00:00Z")) {
            assertThat(client.get(API + "/audit" + query, loggedIn()).code()).isEqualTo(400);
          }
          assertThat(audit.calls).isEmpty();
        });
  }
}
