package co.omnistream.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.bootstrap.config.AppConfiguration;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompositionRootTest {

  @TempDir Path temporary;

  @Test
  void startsTheLocalProfileAndServesHttp() {
    AppConfiguration configuration =
        new AppConfiguration(
            AppConfiguration.LOCAL,
            0,
            temporary.resolve("library"),
            temporary.resolve("omnistream.db"),
            Optional.<URI>empty(),
            Optional.<URI>empty(),
            Path.of("ffmpeg"),
            Path.of("exiftool"),
            true,
            5,
            Duration.ofHours(12));

    try (CompositionRoot root = new CompositionRoot(configuration)) {
      root.start();

      assertThat(root.server()).isNotNull();
      assertThat(root.server().port()).isPositive();
    }
  }
}
