package co.omnistream.domain.model.catalog;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/** Objects shared by the catalog tests. */
public final class CatalogFixtures {

  public static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

  private CatalogFixtures() {}

  /** A SHA-256 fingerprint whose digest is {@code seed} repeated. */
  public static Fingerprint fingerprint(char seed) {
    return new Fingerprint("SHA-256", String.valueOf(seed).repeat(64));
  }

  /** A pending video of 1 000 bytes and one minute. */
  public static MediaFile video(String name, String path, char seed) {
    return MediaFile.create(
        name, Path.of(path), fingerprint(seed), MediaType.VIDEO, 1_000, Duration.ofMinutes(1), NOW);
  }

  /** A pending video with a given size. */
  public static MediaFile videoOfSize(String name, char seed, long sizeBytes) {
    return MediaFile.create(
        name,
        Path.of("/library/" + name),
        fingerprint(seed),
        MediaType.VIDEO,
        sizeBytes,
        Duration.ofMinutes(1),
        NOW);
  }
}
