package co.omnistream.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.bootstrap.config.AppConfiguration;
import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.service.SyncObserver;
import co.omnistream.domain.snapshot.UserSnapshot;
import co.omnistream.presentation.cli.CliAdapter;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompositionRootTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC);
  private static final Pattern PASSWORD = Pattern.compile("Password: (\\S+)");

  @TempDir Path home;

  private final InMemoryFactory factory = new InMemoryFactory();
  private final ByteArrayOutputStream console = new ByteArrayOutputStream();

  private AppConfiguration config(int port) {
    return new AppConfiguration(
        AppConfiguration.LOCAL,
        port,
        home.resolve("library"),
        home.resolve("omnistream.db"),
        Optional.empty(),
        Optional.empty(),
        Path.of("ffmpeg"),
        Path.of("exiftool"),
        true,
        5,
        Duration.ofHours(8));
  }

  private CompositionRoot root(int port) {
    return new CompositionRoot(
        config(port), factory, CLOCK, new PrintStream(console, true, StandardCharsets.UTF_8));
  }

  private String console() {
    return console.toString(StandardCharsets.UTF_8);
  }

  private List<UserSnapshot> users() {
    return factory.users.findAll().stream().map(User::toSnapshot).toList();
  }

  @Test
  void runsACommandAsTheSystemAccount() {
    int code = root(0).runCommand(List.of("help"));

    assertThat(code).isEqualTo(CliAdapter.OK);
    UserSnapshot system =
        factory
            .users
            .findByIdentity(new LinkedIdentity(LinkedIdentity.SYSTEM_ISSUER, "omnistream"))
            .orElseThrow()
            .toSnapshot();
    assertThat(system.role()).isEqualTo(Role.ADMIN);
    assertThat(system.username()).isEqualTo(CompositionRoot.SYSTEM_USERNAME);
    assertThat(factory.credentials.passwords).doesNotContainKey(CompositionRoot.SYSTEM_USERNAME);
  }

  @Test
  void createsTheFirstAdministratorAndShowsItsPasswordOnce() {
    root(0).runCommand(List.of("help"));

    UserSnapshot admin =
        factory.users.findByUsername(CompositionRoot.FIRST_ADMIN).orElseThrow().toSnapshot();
    assertThat(admin.role()).isEqualTo(Role.ADMIN);
    assertThat(admin.identities())
        .containsExactly(
            new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, CompositionRoot.FIRST_ADMIN));
    Matcher shown = PASSWORD.matcher(console());
    assertThat(shown.find()).isTrue();
    assertThat(factory.credentials.passwords)
        .containsEntry(CompositionRoot.FIRST_ADMIN, shown.group(1));
  }

  @Test
  void aSecondStartCreatesNobody() {
    root(0).runCommand(List.of("help"));
    console.reset();

    root(0).runCommand(List.of("help"));

    assertThat(users()).hasSize(2);
    assertThat(console()).doesNotContain("Password");
  }

  @Test
  void anExistingAdministratorIsEnough() {
    factory.users.save(
        User.create("ana", Role.ADMIN, new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, "ana")));

    root(0).runCommand(List.of("help"));

    assertThat(factory.users.findByUsername(CompositionRoot.FIRST_ADMIN)).isEmpty();
    assertThat(console()).doesNotContain("Password");
  }

  @Test
  void anInactiveAdministratorIsNotEnough() {
    User ana =
        User.create("ana", Role.ADMIN, new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, "ana"));
    ana.deactivate();
    factory.users.save(ana);

    root(0).runCommand(List.of("help"));

    assertThat(factory.users.findByUsername(CompositionRoot.FIRST_ADMIN)).isPresent();
  }

  @Test
  void doesNotTakeOverAnAccountCalledAdmin() {
    factory.users.save(
        User.create(
            CompositionRoot.FIRST_ADMIN,
            Role.USER,
            new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, CompositionRoot.FIRST_ADMIN)));

    root(0).runCommand(List.of("help"));

    assertThat(
            factory
                .users
                .findByUsername(CompositionRoot.FIRST_ADMIN)
                .orElseThrow()
                .toSnapshot()
                .role())
        .isEqualTo(Role.USER);
    assertThat(factory.credentials.passwords).isEmpty();
    assertThat(console()).doesNotContain("Password");
  }

  @Test
  void anUnknownCommandIsAUsageError() {
    assertThat(root(0).runCommand(List.of("fly"))).isEqualTo(CliAdapter.USAGE);
  }

  @Test
  void servesTheApiAndTheWebUi() throws Exception {
    try (CompositionRoot root = root(0)) {
      root.start();
      HttpClient http = HttpClient.newHttpClient();
      String base = "http://localhost:" + root.port();

      HttpResponse<String> session =
          http.send(
              HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/session")).build(),
              HttpResponse.BodyHandlers.ofString());
      HttpResponse<String> page =
          http.send(
              HttpRequest.newBuilder(URI.create(base + "/")).build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(session.statusCode()).isEqualTo(401);
      assertThat(page.statusCode()).isEqualTo(200);
      assertThat(page.body()).contains("OmniStream");
    }
  }

  @Test
  void servesTheTextEditorRelay() {
    try (CompositionRoot root = root(0)) {
      root.start();
      URI editor =
          URI.create(
              "ws://localhost:"
                  + root.port()
                  + "/api/v1/files/00000000-0000-4000-8000-0000000000e1/edit");

      assertThatThrownBy(() ->
              HttpClient.newHttpClient()
                  .newWebSocketBuilder()
                  .buildAsync(editor, new WebSocket.Listener() {})
                  .get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .cause()
          .isInstanceOfSatisfying(
              WebSocketHandshakeException.class,
              e -> assertThat(e.getResponse().statusCode()).isEqualTo(401));
    }
  }

  @Test
  void theChangeLogFollowsTheEventBusWhileServing() {
    CompositionRoot root = root(0);
    root.start();
    assertThat(factory.bus.observers).hasExactlyElementsOfTypes(SyncObserver.class);

    root.stop();
    root.stop();

    assertThat(factory.bus.observers).isEmpty();
    assertThatThrownBy(root::port).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void startingTwiceServesOnce() {
    try (CompositionRoot root = root(0)) {
      root.start();
      int port = root.port();
      root.start();

      assertThat(root.port()).isEqualTo(port);
      assertThat(factory.bus.observers).hasSize(1);
    }
  }

  @Test
  void theProfileFactoriesDoNotExistYet() {
    assertThatThrownBy(() -> new CompositionRoot(config(0)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'local' profile");
  }

  @Test
  void passwordsAreLongRandomAndReadable() {
    String one = CompositionRoot.randomPassword();
    String other = CompositionRoot.randomPassword();

    assertThat(one).matches("[a-km-zA-HJ-NP-Z2-9]{5}(-[a-km-zA-HJ-NP-Z2-9]{5}){3}");
    assertThat(one).doesNotContainAnyWhitespaces().isNotEqualTo(other);
  }
}
