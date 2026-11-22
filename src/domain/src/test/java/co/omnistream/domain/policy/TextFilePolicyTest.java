package co.omnistream.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.IndexingStatus;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TextFilePolicyTest {

  private final TextFilePolicy policy = new TextFilePolicy();

  private static MediaFileSnapshot file(String path, long size) {
    return new MediaFileSnapshot(
        new ItemId(UUID.randomUUID()),
        Path.of(path).getFileName().toString(),
        Path.of(path),
        new Fingerprint("SHA-256", "a".repeat(64)),
        MediaType.DOCUMENT,
        size,
        Duration.ZERO,
        IndexingStatus.INDEXED,
        Instant.parse("2026-11-22T10:00:00Z"),
        List.of(),
        Set.of());
  }

  @Test
  void notesMarkdownSubtitlesAndCodeAreText() {
    for (String path :
        List.of("/l/notes.txt", "/l/README.md", "/l/movie.srt", "/l/Main.java", "/l/app.py")) {
      assertThat(policy.isText(file(path, 100))).as(path).isTrue();
    }
  }

  @Test
  void theExtensionIsReadWithoutCase() {
    assertThat(policy.isText(file("/l/NOTES.TXT", 100))).isTrue();
  }

  @Test
  void mediaAndPdfsAreNotText() {
    for (String path : List.of("/l/trip.mp4", "/l/song.mp3", "/l/photo.jpg", "/l/taxes.pdf")) {
      assertThat(policy.isText(file(path, 100))).as(path).isFalse();
    }
  }

  @Test
  void aFileWithoutExtensionIsNotText() {
    assertThat(policy.isText(file("/l/README", 100))).isFalse();
    assertThat(policy.isText(file("/l/.hidden", 100))).isFalse();
    assertThat(policy.isText(file("/l/odd.", 100))).isFalse();
  }

  @Test
  void textFilesHaveAtMostOneMebibyte() {
    assertThat(policy.isText(file("/l/big.txt", TextFilePolicy.MAX_BYTES))).isTrue();
    assertThat(policy.isText(file("/l/big.txt", TextFilePolicy.MAX_BYTES + 1L))).isFalse();
  }

  @Test
  void decodesUtf8() {
    byte[] bytes = "Canción: ñandú".getBytes(StandardCharsets.UTF_8);

    assertThat(policy.decode(bytes)).contains("Canción: ñandú");
  }

  @Test
  void otherEncodingsAreNotDecoded() {
    byte[] latin1 = "Canción".getBytes(StandardCharsets.ISO_8859_1);

    assertThat(policy.decode(latin1)).isEmpty();
  }

  @Test
  void tooManyBytesAreNotDecoded() {
    assertThat(policy.decode(new byte[TextFilePolicy.MAX_BYTES + 1])).isEmpty();
    assertThat(policy.decode(new byte[TextFilePolicy.MAX_BYTES])).isPresent();
  }
}
