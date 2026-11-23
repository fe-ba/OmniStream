package co.omnistream.domain.port.out.contract;

import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.catalog.CatalogId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.MediaFile;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Objects shared by the port contracts (ADR 0018). */
public final class PortFixtures {

  /** A fixed instant, so every assertion is reproducible. */
  public static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  private PortFixtures() {}

  /** An empty catalog, as the repository creates one the first time. */
  public static CatalogIndex emptyCatalog() {
    return CatalogIndex.fromSnapshot(
        new CatalogSnapshot(
            new CatalogId(UUID.randomUUID()), "OmniStream", 0, List.of(), List.of()));
  }

  /** A pending video; the catalog marks it indexed when it is ingested. */
  public static MediaFile video(String name, char seed) {
    return file(name, seed, MediaType.VIDEO, Duration.ofMinutes(90));
  }

  /** A pending audio file. */
  public static MediaFile audio(String name, char seed) {
    return file(name, seed, MediaType.AUDIO, Duration.ofMinutes(45));
  }

  /** A pending document, with no duration. */
  public static MediaFile document(String name, char seed) {
    return file(name, seed, MediaType.DOCUMENT, Duration.ZERO);
  }

  /** An active user with a local identity. */
  public static User user(String username) {
    return User.create(
        username, Role.USER, new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, username));
  }

  private static MediaFile file(String name, char seed, MediaType type, Duration duration) {
    return MediaFile.create(
        name,
        Path.of("/library/" + name),
        new Fingerprint("SHA-256", String.valueOf(seed).repeat(64)),
        type,
        1_024,
        duration,
        NOW);
  }
}
