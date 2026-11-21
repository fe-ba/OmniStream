package co.omnistream.bootstrap.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the composition root needs to know before building the application, read once at
 * startup by {@link ConfigurationLoader} (ADR 0022).
 *
 * @param profile {@value #LOCAL} (desktop app) or {@value #SERVER}; selects the infrastructure
 *     factory
 * @param httpPort port of the REST API and the web UI
 * @param libraryPath folder with the media files
 * @param databasePath SQLite database file, used when there is no {@code databaseUrl}
 * @param databaseUrl JDBC URL of a PostgreSQL database; when present, it is used instead of SQLite
 * @param oidcIssuer URL of an OIDC provider (version 2.0)
 * @param ffmpegPath the FFmpeg executable; a bare name is looked up in the {@code PATH}
 * @param exifToolPath the ExifTool executable; a bare name is looked up in the {@code PATH}
 * @param cacheEnabled whether the catalog repository and the transcoder are cached
 * @param identityFailureThreshold failed calls to an identity provider before it is skipped for a
 *     while
 * @param tokenLifetime how long a session token lasts
 */
public record AppConfiguration(
    String profile,
    int httpPort,
    Path libraryPath,
    Path databasePath,
    Optional<URI> databaseUrl,
    Optional<URI> oidcIssuer,
    Path ffmpegPath,
    Path exifToolPath,
    boolean cacheEnabled,
    int identityFailureThreshold,
    Duration tokenLifetime) {

  /** Profile of the desktop app, which is also the local server (ADR 0010). */
  public static final String LOCAL = "local";

  /** Profile of a server that others connect to. */
  public static final String SERVER = "server";

  public AppConfiguration {
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(libraryPath, "libraryPath");
    Objects.requireNonNull(databasePath, "databasePath");
    Objects.requireNonNull(databaseUrl, "databaseUrl");
    Objects.requireNonNull(oidcIssuer, "oidcIssuer");
    Objects.requireNonNull(ffmpegPath, "ffmpegPath");
    Objects.requireNonNull(exifToolPath, "exifToolPath");
    Objects.requireNonNull(tokenLifetime, "tokenLifetime");
  }
}
