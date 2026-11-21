package co.omnistream.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusUtil;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks that {@code logback.xml} loads without errors and writes to {@code <home>/logs}. */
class LoggingConfigurationTest {

  @Test
  void theLoggingConfigurationWritesToTheHomeFolder(@TempDir Path home) throws Exception {
    System.setProperty("OMNISTREAM_HOME", home.toString());
    // A context of its own, so the test does not touch the one SLF4J uses
    LoggerContext context = new LoggerContext();
    context.setMDCAdapter(new LogbackMDCAdapter());
    try {
      JoranConfigurator configurator = new JoranConfigurator();
      configurator.setContext(context);
      configurator.doConfigure(getClass().getResource("/logback.xml"));

      context.getLogger("co.omnistream.test").info("Logging works");

      assertThat(new StatusUtil(context).getHighestLevel(0)).isLessThan(Status.ERROR);
      assertThat(home.resolve("logs").resolve("omnistream.log")).exists();
    } finally {
      context.stop();
      System.clearProperty("OMNISTREAM_HOME");
    }
  }
}
