package co.omnistream.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationLoaderTest {

  @TempDir Path userHome;

  private Path home() {
    return userHome.resolve(".omnistream");
  }

  private AppConfiguration load(Map<String, String> environment, String... args) {
    return new ConfigurationLoader(environment, userHome).load(List.of(args));
  }

  private void writeFile(Path file, String content) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  @Test
  void withoutFileNorVariablesEverythingHasItsDefault() {
    AppConfiguration config = load(Map.of());

    assertThat(config.profile()).isEqualTo("local");
    assertThat(config.httpPort()).isEqualTo(8080);
    assertThat(config.libraryPath()).isEqualTo(home().resolve("library"));
    assertThat(config.databasePath()).isEqualTo(home().resolve("omnistream.db"));
    assertThat(config.databaseUrl()).isEmpty();
    assertThat(config.oidcIssuer()).isEmpty();
    assertThat(config.ffmpegPath()).isEqualTo(Path.of("ffmpeg"));
    assertThat(config.exifToolPath()).isEqualTo(Path.of("exiftool"));
    assertThat(config.cacheEnabled()).isTrue();
    assertThat(config.identityFailureThreshold()).isEqualTo(5);
    assertThat(config.tokenLifetime()).isEqualTo(Duration.ofHours(12));
  }

  @Test
  void theFileInTheHomeFolderChangesTheDefaults() throws IOException {
    writeFile(
        home().resolve(ConfigurationLoader.FILE_NAME),
        """
        profile = server
        http.port = 9090
        library.path = /media/library
        database.url = jdbc:postgresql://db:5432/omnistream
        cache.enabled = false
        identity.failure-threshold = 3
        token.lifetime = PT2H
        """);

    AppConfiguration config = load(Map.of());

    assertThat(config.profile()).isEqualTo("server");
    assertThat(config.httpPort()).isEqualTo(9090);
    assertThat(config.libraryPath()).isEqualTo(Path.of("/media/library"));
    assertThat(config.databaseUrl()).contains(URI.create("jdbc:postgresql://db:5432/omnistream"));
    assertThat(config.cacheEnabled()).isFalse();
    assertThat(config.identityFailureThreshold()).isEqualTo(3);
    assertThat(config.tokenLifetime()).isEqualTo(Duration.ofHours(2));
  }

  @Test
  void anEnvironmentVariableWinsOverTheFile() throws IOException {
    writeFile(home().resolve(ConfigurationLoader.FILE_NAME), "http.port = 9090\n");

    AppConfiguration config = load(Map.of("OMNISTREAM_HTTP_PORT", "7070"));

    assertThat(config.httpPort()).isEqualTo(7070);
  }

  @Test
  void variableNamesComeFromTheKeys() {
    assertThat(ConfigurationLoader.environmentName("library.path"))
        .isEqualTo("OMNISTREAM_LIBRARY_PATH");
    assertThat(ConfigurationLoader.environmentName("identity.failure-threshold"))
        .isEqualTo("OMNISTREAM_IDENTITY_FAILURE_THRESHOLD");
  }

  @Test
  void omnistreamHomeMovesTheHomeFolderAndItsFile() throws IOException {
    Path elsewhere = userHome.resolve("data");
    writeFile(elsewhere.resolve(ConfigurationLoader.FILE_NAME), "http.port = 9191\n");

    AppConfiguration config = load(Map.of("OMNISTREAM_HOME", elsewhere.toString()));

    assertThat(config.httpPort()).isEqualTo(9191);
    assertThat(config.libraryPath()).isEqualTo(elsewhere.resolve("library"));
  }

  @Test
  void theFileCanBeGivenOnTheCommandLine() throws IOException {
    Path file = userHome.resolve("custom.properties");
    writeFile(file, "http.port = 9292\n");

    assertThat(load(Map.of(), "--config", file.toString()).httpPort()).isEqualTo(9292);
    assertThat(load(Map.of(), "--config=" + file).httpPort()).isEqualTo(9292);
  }

  @Test
  void aFileGivenOnTheCommandLineMustExist() {
    assertThatThrownBy(() -> load(Map.of(), "--config", "/nowhere/omnistream.properties"))
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("does not exist");
    assertThatThrownBy(() -> load(Map.of(), "--config")).isInstanceOf(ConfigurationException.class);
  }

  @Test
  void aBlankValueCountsAsNotSet() {
    AppConfiguration config =
        load(Map.of("OMNISTREAM_DATABASE_URL", "  ", "OMNISTREAM_HTTP_PORT", ""));

    assertThat(config.databaseUrl()).isEqualTo(Optional.empty());
    assertThat(config.httpPort()).isEqualTo(8080);
  }

  @Test
  void aTildeMeansTheUserHomeFolder() {
    AppConfiguration config = load(Map.of("OMNISTREAM_LIBRARY_PATH", "~/Music"));

    assertThat(config.libraryPath()).isEqualTo(userHome.resolve("Music"));
  }

  @Test
  void anInvalidValueStopsTheStartupNamingTheKey() {
    assertThatThrownBy(() -> load(Map.of("OMNISTREAM_HTTP_PORT", "eighty")))
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("http.port")
        .hasMessageContaining("OMNISTREAM_HTTP_PORT");
    assertThatThrownBy(() -> load(Map.of("OMNISTREAM_HTTP_PORT", "70000")))
        .hasMessageContaining("http.port");
    assertThatThrownBy(() -> load(Map.of("OMNISTREAM_PROFILE", "cloud")))
        .hasMessageContaining("profile");
    assertThatThrownBy(() -> load(Map.of("OMNISTREAM_TOKEN_LIFETIME", "12 hours")))
        .hasMessageContaining("token.lifetime");
    assertThatThrownBy(() -> load(Map.of("OMNISTREAM_TOKEN_LIFETIME", "PT0S")))
        .hasMessageContaining("token.lifetime");
    assertThatThrownBy(() -> load(Map.of("OMNISTREAM_CACHE_ENABLED", "yes")))
        .hasMessageContaining("cache.enabled");
    assertThatThrownBy(() -> load(Map.of("OMNISTREAM_DATABASE_URL", "not a url")))
        .hasMessageContaining("database.url");
  }

  @Test
  void theProfileIgnoresCase() {
    assertThat(load(Map.of("OMNISTREAM_PROFILE", "SERVER")).profile()).isEqualTo("server");
  }
}
