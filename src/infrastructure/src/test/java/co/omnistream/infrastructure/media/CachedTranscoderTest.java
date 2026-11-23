package co.omnistream.infrastructure.media;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.StreamProfile;
import co.omnistream.domain.port.out.ITranscoder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CachedTranscoderTest {

  private static final StreamProfile PROFILE =
      new StreamProfile(Optional.of("h264"), Optional.of(1_000), Optional.of("720p"));

  @TempDir Path temporary;

  @Test
  void convertsOnceAndThenReadsFromTheCache() throws IOException {
    CountingTranscoder delegate = new CountingTranscoder();
    CachedTranscoder transcoder = new CachedTranscoder(delegate, temporary, 1_000_000);

    String first = read(transcoder.transcode(Path.of("video.mkv"), PROFILE, Duration.ZERO));
    String second = read(transcoder.transcode(Path.of("video.mkv"), PROFILE, Duration.ZERO));

    assertThat(first).isEqualTo("bytes");
    assertThat(second).isEqualTo("bytes");
    assertThat(delegate.conversions()).isEqualTo(1);
  }

  @Test
  void keepsDifferentPositionsApart() throws IOException {
    CountingTranscoder delegate = new CountingTranscoder();
    CachedTranscoder transcoder = new CachedTranscoder(delegate, temporary, 1_000_000);

    read(transcoder.transcode(Path.of("video.mkv"), PROFILE, Duration.ZERO));
    read(transcoder.transcode(Path.of("video.mkv"), PROFILE, Duration.ofMinutes(1)));

    assertThat(delegate.conversions()).isEqualTo(2);
  }

  @Test
  void evictsTheOldestFileWhenTheCacheIsFull() throws IOException {
    CountingTranscoder delegate = new CountingTranscoder();
    CachedTranscoder transcoder = new CachedTranscoder(delegate, temporary, 6);

    read(transcoder.transcode(Path.of("video.mkv"), PROFILE, Duration.ZERO));
    read(transcoder.transcode(Path.of("video.mkv"), PROFILE, Duration.ofMinutes(1)));

    try (Stream<Path> files = Files.list(temporary)) {
      assertThat(files.filter(path -> path.toString().endsWith(".media"))).hasSize(1);
    }
  }

  private static String read(MediaStream stream) throws IOException {
    try (var data = stream.data()) {
      return new String(data.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static final class CountingTranscoder implements ITranscoder {
    private final AtomicInteger conversions = new AtomicInteger();

    int conversions() {
      return conversions.get();
    }

    @Override
    public boolean supports(MediaType source, StreamProfile p) {
      return true;
    }

    @Override
    public MediaStream transcode(Path path, StreamProfile p, Duration pos) {
      conversions.incrementAndGet();
      return new MediaStream(
          new ByteArrayInputStream("bytes".getBytes(StandardCharsets.UTF_8)),
          "video/mp4",
          true,
          Optional.of(5L));
    }
  }
}
