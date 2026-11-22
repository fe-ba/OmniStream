package co.omnistream.bootstrap;

import co.omnistream.bootstrap.config.AppConfiguration;
import co.omnistream.bootstrap.config.ConfigurationLoader;
import java.util.List;

/**
 * Entry point of the application: it reads the configuration and starts the composition root.
 *
 * <p>It has no logic beyond that. Everything else happens in {@link CompositionRoot}, which reads
 * the profile and wires the adapters of that profile (ADR 0013).
 */
public final class Main {

  private Main() {}

  public static void main(String[] args) {
    AppConfiguration configuration = new ConfigurationLoader().load(List.of(args));
    CompositionRoot root = new CompositionRoot(configuration);
    Runtime.getRuntime().addShutdownHook(new Thread(root::close, "omnistream-shutdown"));
    root.start();
  }
}
