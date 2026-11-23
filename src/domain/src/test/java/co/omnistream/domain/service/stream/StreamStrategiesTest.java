package co.omnistream.domain.service.stream;

import static co.omnistream.domain.model.catalog.CatalogFixtures.NOW;
import static co.omnistream.domain.model.catalog.CatalogFixtures.fingerprint;
import static co.omnistream.domain.model.catalog.CatalogFixtures.video;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.catalog.IndexingStatus;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.StreamProfile;
import co.omnistream.domain.port.out.ITranscoder;
import co.omnistream.domain.port.out.memory.InMemoryFileStorage;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StreamStrategiesTest {

  private static final StreamProfile DIRECT =
      new StreamProfile(Optional.empty(), Optional.empty(), Optional.empty());
  private static final StreamProfile TRANSCODE_720P =
      new StreamProfile(Optional.of("h264"), Optional.of(1500), Optional.of("720p"));

  private final InMemoryFileStorage storage = new InMemoryFileStorage();
  private final StubTranscoder transcoder = new StubTranscoder();

  private void put(Path path, byte[] bytes) {
    storage.write(path, new ByteArrayInputStream(bytes));
  }

  @Test
  void directStreamSupportsOnlyDirectPlayAndServesFullBytesWithResolvedMime() throws IOException {
    Path videoPath = Path.of("/library/movie.mkv");
    byte[] raw = "matroska-bytes".getBytes(StandardCharsets.UTF_8);
    put(videoPath, raw);

    MediaFileSnapshot video =
        snapshot("Movie", videoPath, MediaType.VIDEO, raw.length, Duration.ofMinutes(2));
    MediaFileSnapshot unknownDoc =
        snapshot("Notes", Path.of("/library/readme"), MediaType.DOCUMENT, 12L, Duration.ZERO);
    put(Path.of("/library/readme"), "notes-12-byte".getBytes(StandardCharsets.UTF_8));
    MediaFileSnapshot unknownVideo =
        snapshot("RawStream", Path.of("/library/stream"), MediaType.VIDEO, 4L, Duration.ZERO);
    put(Path.of("/library/stream"), new byte[] {1, 2, 3, 4});

    DirectStream strategy = new DirectStream(storage);

    assertThat(strategy.supports(video, DIRECT)).isTrue();
    assertThat(strategy.supports(video, TRANSCODE_720P)).isFalse();

    MediaStream videoStream = strategy.generateStream(video, DIRECT, Duration.ofSeconds(30));
    try (InputStream data = videoStream.data()) {
      assertThat(videoStream.mime()).isEqualTo("video/x-matroska");
      assertThat(videoStream.transcoded()).isFalse();
      assertThat(videoStream.sizeBytes()).contains((long) raw.length);
      assertThat(data.readAllBytes()).isEqualTo(raw);
    }

    MediaStream docStream = strategy.generateStream(unknownDoc, DIRECT, Duration.ZERO);
    try (InputStream data = docStream.data()) {
      assertThat(docStream.mime()).isEqualTo("application/octet-stream");
      assertThat(docStream.sizeBytes()).contains(12L);
      assertThat(data.readAllBytes()).isNotEmpty();
    }

    MediaStream fallbackVideo = strategy.generateStream(unknownVideo, DIRECT, Duration.ZERO);
    try (InputStream data = fallbackVideo.data()) {
      assertThat(fallbackVideo.mime()).isEqualTo("video/mp4");
      assertThat(data.readAllBytes()).containsExactly(1, 2, 3, 4);
    }
  }

  @Test
  void directStreamFallsBackToFileNameExtensionAndFailsWhenMissingInStorage() throws IOException {
    Path extensionlessPath = Path.of("/blobs/001");
    byte[] audioBytes = "flac-bytes".getBytes(StandardCharsets.UTF_8);
    put(extensionlessPath, audioBytes);

    MediaFileSnapshot audio =
        snapshot(
            "album.flac", extensionlessPath, MediaType.AUDIO, audioBytes.length, Duration.ZERO);
    DirectStream strategy = new DirectStream(storage);

    MediaStream stream = strategy.generateStream(audio, DIRECT, Duration.ZERO);
    try (InputStream data = stream.data()) {
      assertThat(stream.mime()).isEqualTo("audio/flac");
      assertThat(data.readAllBytes()).isEqualTo(audioBytes);
    }

    MediaFileSnapshot missing = video("Missing", "/library/missing.mp4", '0').toSnapshot();
    assertThatThrownBy(() -> strategy.generateStream(missing, DIRECT, Duration.ZERO))
        .isInstanceOf(ResourceNotFound.class);
  }

  @Test
  void transcodedStreamDelegatesSupportAndTranscodingToPort() throws IOException {
    MediaFileSnapshot clip = video("Clip", "/library/clip.avi", 'a').toSnapshot();
    MediaFileSnapshot photo =
        snapshot("Photo", Path.of("/library/photo.png"), MediaType.IMAGE, 50L, Duration.ZERO);
    TranscodedStream strategy = new TranscodedStream(transcoder);

    assertThat(strategy.supports(clip, TRANSCODE_720P)).isTrue();
    assertThat(strategy.supports(clip, DIRECT)).isFalse();
    assertThat(strategy.supports(photo, TRANSCODE_720P)).isFalse();

    MediaStream result = strategy.generateStream(clip, TRANSCODE_720P, Duration.ofSeconds(45));
    try (InputStream data = result.data()) {
      assertThat(result.transcoded()).isTrue();
      assertThat(result.sizeBytes()).isEmpty();
      assertThat(result.mime()).isEqualTo("video/mp4");
      assertThat(transcoder.lastPath).isEqualTo(Path.of("/library/clip.avi"));
      assertThat(transcoder.lastProfile).isEqualTo(TRANSCODE_720P);
      assertThat(transcoder.lastPosition).isEqualTo(Duration.ofSeconds(45));
      assertThat(new String(data.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("transcoded");
    }
  }

  private static MediaFileSnapshot snapshot(
      String name, Path path, MediaType type, long sizeBytes, Duration duration) {
    return new MediaFileSnapshot(
        new ItemId(UUID.randomUUID()),
        name,
        path,
        fingerprint('a'),
        type,
        sizeBytes,
        duration,
        IndexingStatus.INDEXED,
        NOW,
        List.of(),
        Set.of());
  }

  private static final class StubTranscoder implements ITranscoder {
    Path lastPath;
    StreamProfile lastProfile;
    Duration lastPosition;

    @Override
    public boolean supports(MediaType source, StreamProfile p) {
      return source == MediaType.VIDEO && !p.isDirectPlay();
    }

    @Override
    public MediaStream transcode(Path path, StreamProfile p, Duration pos) {
      this.lastPath = path;
      this.lastProfile = p;
      this.lastPosition = pos;
      return new MediaStream(
          new ByteArrayInputStream("transcoded".getBytes(StandardCharsets.UTF_8)),
          "video/mp4",
          true,
          Optional.empty());
    }
  }
}
