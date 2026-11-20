# 0022. Configuration from a properties file and the environment

- **Status:** Accepted
- **Date:** 2026-11-20

## Context

The composition root needs an `AppConfiguration` before it builds anything: the
profile that selects the infrastructure factory, the paths to the library, the
database, FFmpeg and ExifTool, the HTTP port, and a few limits.

OmniStream runs in two ways (ADR 0010): as the desktop app, where the user
starts it and nobody sets environment variables, and as a server, later in a
Docker container (ADR 0017), where environment variables are the usual way in.
The configuration is a flat list of values.

## Decision

- The configuration is read **once, at startup**, by the bootstrap module, into
  the `AppConfiguration` record. Nothing else reads files or environment
  variables for configuration.
- Each value is looked up in this order, and the first one found wins:
  1. an environment variable;
  2. the file `omnistream.properties`;
  3. a default written in the code.
- The environment variable for a key is `OMNISTREAM_` plus the key in upper case
  with dots and dashes turned into underscores: `library.path` becomes
  `OMNISTREAM_LIBRARY_PATH`.
- The file is the one given with `--config <file>`. Without it, the file is
  `<home>/omnistream.properties`, where `<home>` is `OMNISTREAM_HOME` or, if it is
  not set, `~/.omnistream`. A missing file is not an error: the defaults apply.
- Keys and defaults:

  | Key | `AppConfiguration` | Default |
  |---|---|---|
  | `profile` | `profile` | `local` (or `server`) |
  | `http.port` | `httpPort` | `8080` |
  | `library.path` | `libraryPath` | `<home>/library` |
  | `database.path` | `databasePath` | `<home>/omnistream.db` (SQLite) |
  | `database.url` | `databaseUrl` | none; when set, PostgreSQL is used |
  | `oidc.issuer` | `oidcIssuer` | none (OIDC is for version 2.0) |
  | `ffmpeg.path` | `ffmpegPath` | `ffmpeg`, looked up in the `PATH` |
  | `exiftool.path` | `exifToolPath` | `exiftool`, looked up in the `PATH` |
  | `cache.enabled` | `cacheEnabled` | `true` |
  | `identity.failure-threshold` | `identityFailureThreshold` | `5` |
  | `token.lifetime` | `tokenLifetime` | `PT12H` (ISO-8601 duration) |

- Secrets, such as the database password inside `database.url`, go in
  environment variables, not in the file.
- An invalid value (a port that is not a number, a duration that does not
  parse, an unknown profile) stops the startup with a message that names the
  key. The application never starts with half its configuration.
- The library path in the file is only the starting value. Once an
  administrator changes it from the application (`configureLibraryPath`), the
  value stored through `IConfigurationRepository` wins.
- `AppConfiguration` gains the `httpPort` field; the ports and adapters
  diagrams show it.

## Consequences

- The desktop app works with no configuration at all, and a container is
  configured with environment variables only.
- No library is needed: `java.util.Properties` reads the file.
- Every new setting needs a key, a default and a line in the table above.
- The file holds no nesting. If the configuration ever needs it, this decision
  has to be revisited.
