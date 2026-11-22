package co.omnistream.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.bootstrap.config.ConfigurationLoader;
import co.omnistream.presentation.cli.CliAdapter;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

  @TempDir Path home;

  private final ByteArrayOutputStream errors = new ByteArrayOutputStream();
  private final ByteArrayOutputStream console = new ByteArrayOutputStream();

  private int run(String... args) {
    return Main.run(
        List.of(args),
        new ConfigurationLoader(Map.of(), home),
        config ->
            new CompositionRoot(
                config,
                new InMemoryFactory(),
                Clock.systemUTC(),
                new PrintStream(console, true, StandardCharsets.UTF_8)),
        new PrintStream(errors, true, StandardCharsets.UTF_8));
  }

  private String errors() {
    return errors.toString(StandardCharsets.UTF_8);
  }

  @Test
  void theConfigOptionIsNotPartOfTheCommand() {
    assertThat(Main.withoutConfigOption(List.of("--config", "a.properties", "ingest", "x.mp3")))
        .containsExactly("ingest", "x.mp3");
    assertThat(Main.withoutConfigOption(List.of("status", "--config=a.properties")))
        .containsExactly("status");
    assertThat(Main.withoutConfigOption(List.of())).isEmpty();
  }

  @Test
  void runsTheCommandAndAnswersItsExitCode() {
    assertThat(run("help")).isEqualTo(CliAdapter.OK);
    assertThat(run("fly")).isEqualTo(CliAdapter.USAGE);
  }

  @Test
  void readsTheGivenConfigurationBeforeTheCommand() throws Exception {
    Path file = Files.writeString(home.resolve("my.properties"), "http.port=9090\n");
    AtomicReference<Integer> port = new AtomicReference<>();

    int code =
        Main.run(
            List.of("--config", file.toString(), "help"),
            new ConfigurationLoader(Map.of(), home),
            config -> {
              port.set(config.httpPort());
              return new CompositionRoot(
                  config,
                  new InMemoryFactory(),
                  Clock.systemUTC(),
                  new PrintStream(console, true, StandardCharsets.UTF_8));
            },
            new PrintStream(errors, true, StandardCharsets.UTF_8));

    assertThat(code).isEqualTo(CliAdapter.OK);
    assertThat(port.get()).isEqualTo(9090);
  }

  @Test
  void aWrongConfigurationIsAUsageError() {
    assertThat(run("--config", home.resolve("missing.properties").toString()))
        .isEqualTo(CliAdapter.USAGE);
    assertThat(errors()).startsWith("omnistream: ");
  }

  @Test
  void aMissingInfrastructureFailsWithItsReason() {
    int code =
        Main.run(
            List.of("help"),
            new ConfigurationLoader(Map.of(), home),
            CompositionRoot::new,
            new PrintStream(errors, true, StandardCharsets.UTF_8));

    assertThat(code).isEqualTo(CliAdapter.FAILED);
    assertThat(errors()).contains("not available yet");
  }
}
