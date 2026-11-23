package co.omnistream.domain.port.out.memory;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.StreamProfile;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InMemoryTranscoderTest {

  private static final StreamProfile PROFILE =
      new StreamProfile(Optional.of("h264"), Optional.empty(), Optional.empty());

  @Test
  void answersTheConfiguredStreamForTheSupportedType() throws IOException {
    InMemoryTranscoder transcoder =
        new InMemoryTranscoder(
            "bytes".getBytes(UTF_8), "video/mp4", true, Optional.of(5L), MediaType.VIDEO);

    assertThat(transcoder.supports(MediaType.VIDEO, PROFILE)).isTrue();
    assertThat(transcoder.supports(MediaType.AUDIO, PROFILE)).isFalse();
    MediaStream stream = transcoder.transcode(Path.of("video.mkv"), PROFILE, Duration.ZERO);
    try (var data = stream.data()) {
      assertThat(new String(data.readAllBytes(), UTF_8)).isEqualTo("bytes");
    }
    assertThat(stream.mime()).isEqualTo("video/mp4");
    assertThat(stream.transcoded()).isTrue();
    assertThat(stream.sizeBytes()).contains(5L);
  }
}
