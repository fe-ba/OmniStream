package co.omnistream.bootstrap;

import co.omnistream.bootstrap.config.AppConfiguration;
import co.omnistream.bootstrap.config.ConfigurationException;
import co.omnistream.bootstrap.config.ConfigurationLoader;
import co.omnistream.presentation.cli.CliAdapter;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Entry point of OmniStream. Without arguments it starts the server; with arguments it runs one
 * command of the command line and exits with its code. {@code --config <file>} (or {@code
 * --config=<file>}) chooses the configuration file in both cases (ADR 0022).
 *
 * <pre>
 *   omnistream                        serve the web UI and the REST API
 *   omnistream --config my.properties serve, with another configuration
 *   omnistream ingest ~/Music/song.mp3
 *   omnistream help
 * </pre>
 */
public final class Main {

  /** What {@link #run} answers while the server keeps running. */
  static final int SERVING = -1;

  private Main() {}

  public static void main(String[] args) {
    int code = run(List.of(args), new ConfigurationLoader(), CompositionRoot::new, System.err);
    if (code != SERVING) {
      System.exit(code);
    }
  }

  /**
   * Reads the configuration, builds the application and serves it or runs the command.
   *
   * @return {@link #SERVING} once the server is listening, or the exit code of the command
   */
  static int run(
      List<String> args,
      ConfigurationLoader loader,
      Function<AppConfiguration, CompositionRoot> compose,
      PrintStream errors) {
    AppConfiguration config;
    try {
      config = loader.load(args);
    } catch (ConfigurationException e) {
      errors.println("omnistream: " + e.getMessage());
      return CliAdapter.USAGE;
    }
    List<String> command = withoutConfigOption(args);
    CompositionRoot root;
    try {
      root = compose.apply(config);
    } catch (RuntimeException e) {
      // The database cannot be opened, the server profile has no database URL...
      errors.println("omnistream: " + reason(e));
      return CliAdapter.FAILED;
    }
    if (command.isEmpty()) {
      try {
        root.start();
      } catch (RuntimeException e) {
        // The port is taken, for example
        errors.println("omnistream: the server could not start: " + reason(e));
        root.close();
        return CliAdapter.FAILED;
      }
      // Ctrl+C or a stop of the service: stop serving and close the database
      Runtime.getRuntime().addShutdownHook(new Thread(root::close, "omnistream-shutdown"));
      return SERVING;
    }
    try (root) {
      return root.runCommand(command);
    }
  }

  /** What went wrong, in one line: the message, or the cause's if it has none. */
  static String reason(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t.getMessage() != null && !t.getMessage().isBlank()) {
        return t.getMessage();
      }
    }
    return e.getClass().getSimpleName();
  }

  /** The arguments without {@code --config <file>} or {@code --config=<file>}. */
  static List<String> withoutConfigOption(List<String> args) {
    List<String> rest = new ArrayList<>();
    for (int i = 0; i < args.size(); i++) {
      String arg = args.get(i);
      if (arg.equals("--config")) {
        i++; // skip the file too
      } else if (!arg.startsWith("--config=")) {
        rest.add(arg);
      }
    }
    return List.copyOf(rest);
  }
}
