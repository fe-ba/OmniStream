package co.omnistream.bootstrap.config;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

/**
 * Reads the {@link AppConfiguration} at startup, as ADR 0022 says. Each key is looked up, in order:
 *
 * <ol>
 *   <li>in the environment variable {@code OMNISTREAM_} plus the key in upper case, with dots and
 *       dashes turned into underscores ({@code library.path} becomes {@code
 *       OMNISTREAM_LIBRARY_PATH});
 *   <li>in the file {@code omnistream.properties}: the one given with {@code --config <file>} or,
 *       without it, the one in the home folder;
 *   <li>in the defaults below.
 * </ol>
 *
 * <p>The home folder is {@code OMNISTREAM_HOME} or, if it is not set, {@code ~/.omnistream}. A
 * blank value counts as not set. An invalid value stops the startup with a {@link
 * ConfigurationException} that names the key.
 */
public final class ConfigurationLoader {

  /** Name of the configuration file in the home folder. */
  public static final String FILE_NAME = "omnistream.properties";

  private final Map<String, String> environment;
  private final Path userHome;

  /** A loader that reads the real environment and the real home folder of the user. */
  public ConfigurationLoader() {
    this(System.getenv(), Path.of(System.getProperty("user.home")));
  }

  /** A loader that reads the given environment; for tests. */
  ConfigurationLoader(Map<String, String> environment, Path userHome) {
    this.environment = Map.copyOf(environment);
    this.userHome = Objects.requireNonNull(userHome, "userHome");
  }

  /**
   * Reads the configuration.
   *
   * @param args the command-line arguments; only {@code --config <file>} matters here
   * @throws ConfigurationException if a file cannot be read or a value is invalid
   */
  public AppConfiguration load(List<String> args) {
    Path home = home();
    Properties file = read(configFile(args, home));
    Function<String, Optional<String>> value = key -> lookup(key, file);

    return new AppConfiguration(
        profile(value.apply("profile").orElse(AppConfiguration.LOCAL)),
        number("http.port", value.apply("http.port"), 8080, 1, 65_535),
        path("library.path", value.apply("library.path"), home.resolve("library")),
        path("database.path", value.apply("database.path"), home.resolve("omnistream.db")),
        uri("database.url", value.apply("database.url")),
        uri("oidc.issuer", value.apply("oidc.issuer")),
        path("ffmpeg.path", value.apply("ffmpeg.path"), Path.of("ffmpeg")),
        path("exiftool.path", value.apply("exiftool.path"), Path.of("exiftool")),
        bool("cache.enabled", value.apply("cache.enabled"), true),
        number(
            "identity.failure-threshold",
            value.apply("identity.failure-threshold"),
            5,
            1,
            Integer.MAX_VALUE),
        duration("token.lifetime", value.apply("token.lifetime"), Duration.ofHours(12)));
  }

  /**
   * The environment variable for {@code key}: {@code library.path} gives {@code
   * OMNISTREAM_LIBRARY_PATH}.
   */
  static String environmentName(String key) {
    return "OMNISTREAM_" + key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
  }

  // ------------------------------------------------------------------ where values come from

  private Path home() {
    return lookupEnvironment("OMNISTREAM_HOME")
        .map(value -> path("OMNISTREAM_HOME", value))
        .orElse(userHome.resolve(".omnistream"));
  }

  /**
   * The file given with {@code --config}, which must exist, or the optional one in the home folder.
   */
  private Optional<Path> configFile(List<String> args, Path home) {
    for (int i = 0; i < args.size(); i++) {
      String arg = args.get(i);
      String given = null;
      if (arg.equals("--config")) {
        if (i + 1 >= args.size()) {
          throw new ConfigurationException("--config needs a file");
        }
        given = args.get(i + 1);
      } else if (arg.startsWith("--config=")) {
        given = arg.substring("--config=".length());
      }
      if (given != null) {
        Path file = path("--config", given);
        if (!Files.isRegularFile(file)) {
          throw new ConfigurationException("The configuration file does not exist: " + file);
        }
        return Optional.of(file);
      }
    }
    Path inHome = home.resolve(FILE_NAME);
    return Files.isRegularFile(inHome) ? Optional.of(inHome) : Optional.empty();
  }

  private static Properties read(Optional<Path> file) {
    Properties properties = new Properties();
    if (file.isPresent()) {
      try (Reader reader = Files.newBufferedReader(file.get(), StandardCharsets.UTF_8)) {
        properties.load(reader);
      } catch (IOException | IllegalArgumentException e) {
        throw new ConfigurationException("Cannot read the configuration file " + file.get(), e);
      }
    }
    return properties;
  }

  private Optional<String> lookup(String key, Properties file) {
    return lookupEnvironment(environmentName(key))
        .or(() -> Optional.ofNullable(file.getProperty(key)).map(String::strip))
        .filter(value -> !value.isEmpty());
  }

  private Optional<String> lookupEnvironment(String name) {
    return Optional.ofNullable(environment.get(name)).map(String::strip).filter(v -> !v.isEmpty());
  }

  // ------------------------------------------------------------------ parsing values

  private static String profile(String value) {
    String profile = value.toLowerCase(Locale.ROOT);
    if (!profile.equals(AppConfiguration.LOCAL) && !profile.equals(AppConfiguration.SERVER)) {
      throw invalid("profile", value, "use local or server");
    }
    return profile;
  }

  private static int number(String key, Optional<String> value, int otherwise, int min, int max) {
    if (value.isEmpty()) {
      return otherwise;
    }
    try {
      int number = Integer.parseInt(value.get());
      if (number < min || number > max) {
        throw invalid(key, value.get(), "it must be between " + min + " and " + max);
      }
      return number;
    } catch (NumberFormatException e) {
      throw invalid(key, value.get(), "it must be a whole number");
    }
  }

  private Path path(String key, Optional<String> value, Path otherwise) {
    return value.map(v -> path(key, v)).orElse(otherwise);
  }

  /** A path; {@code ~/} at the start means the user's home folder. */
  private Path path(String key, String value) {
    try {
      if (value.equals("~")) {
        return userHome;
      }
      if (value.startsWith("~/")) {
        return userHome.resolve(value.substring(2));
      }
      return Path.of(value);
    } catch (InvalidPathException e) {
      throw invalid(key, value, "it is not a valid path");
    }
  }

  private static Optional<URI> uri(String key, Optional<String> value) {
    try {
      return value.map(URI::create);
    } catch (IllegalArgumentException e) {
      throw invalid(key, value.orElseThrow(), "it is not a valid URL");
    }
  }

  private static boolean bool(String key, Optional<String> value, boolean otherwise) {
    if (value.isEmpty()) {
      return otherwise;
    }
    return switch (value.get().toLowerCase(Locale.ROOT)) {
      case "true" -> true;
      case "false" -> false;
      default -> throw invalid(key, value.get(), "use true or false");
    };
  }

  private static Duration duration(String key, Optional<String> value, Duration otherwise) {
    if (value.isEmpty()) {
      return otherwise;
    }
    try {
      Duration duration = Duration.parse(value.get());
      if (!duration.isPositive()) {
        throw invalid(key, value.get(), "it must be longer than zero");
      }
      return duration;
    } catch (DateTimeParseException e) {
      throw invalid(key, value.get(), "it must be an ISO-8601 duration, such as PT12H");
    }
  }

  private static ConfigurationException invalid(String key, String value, String hint) {
    String where = key.matches("[a-z.-]+") ? "'" + key + "' (" + environmentName(key) + ")" : key;
    return new ConfigurationException("Invalid value '" + value + "' for " + where + ": " + hint);
  }
}
