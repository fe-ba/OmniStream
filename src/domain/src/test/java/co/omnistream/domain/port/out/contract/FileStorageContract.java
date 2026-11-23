package co.omnistream.domain.port.out.contract;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.port.out.IFileStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Behaviour every {@link IFileStorage} must share (ADR 0018). */
public abstract class FileStorageContract {

  protected abstract IFileStorage createStorage();

  @Test
  void writesReadsOverwritesAndDeletesAFile() {
    IFileStorage storage = createStorage();
    Path file = Path.of("shows/clip.mp4");

    storage.write(file, stream("first"));

    assertThat(storage.exists(file)).isTrue();
    assertThat(read(storage, file)).isEqualTo("first");

    storage.write(file, stream("second"));

    assertThat(read(storage, file)).isEqualTo("second");

    storage.delete(file);

    assertThat(storage.exists(file)).isFalse();
  }

  @Test
  void listsOnlyTheFilesUnderAFolder() {
    IFileStorage storage = createStorage();
    storage.write(Path.of("shows/a.mp4"), stream("a"));
    storage.write(Path.of("shows/nested/b.mp4"), stream("b"));
    storage.write(Path.of("other/c.mp4"), stream("c"));

    assertThat(storage.list(Path.of("shows")))
        .extracting(path -> path.getFileName().toString())
        .containsExactlyInAnyOrder("a.mp4", "b.mp4");
  }

  @Test
  void computesTheSha256FingerprintOfTheContent() {
    IFileStorage storage = createStorage();
    storage.write(Path.of("hello.txt"), stream("hello"));

    assertThat(storage.computeFingerprint(Path.of("hello.txt")))
        .isEqualTo(
            new Fingerprint(
                "SHA-256", "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"));
  }

  @Test
  void copiesAFile() {
    IFileStorage storage = createStorage();
    storage.write(Path.of("source.mp4"), stream("content"));

    storage.copy(Path.of("source.mp4"), Path.of("copy.mp4"));

    assertThat(storage.exists(Path.of("source.mp4"))).isTrue();
    assertThat(storage.exists(Path.of("copy.mp4"))).isTrue();
    assertThat(read(storage, Path.of("copy.mp4"))).isEqualTo("content");
  }

  @Test
  void reportsTheAvailableSpace() {
    assertThat(createStorage().freeSpace()).isGreaterThanOrEqualTo(0);
  }

  private static ByteArrayInputStream stream(String text) {
    return new ByteArrayInputStream(text.getBytes(UTF_8));
  }

  private static String read(IFileStorage storage, Path path) {
    try (InputStream data = storage.read(path)) {
      return new String(data.readAllBytes(), UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + path, e);
    }
  }
}
