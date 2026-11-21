# 0021. Logging with SLF4J and Logback

- **Status:** Accepted
- **Date:** 2026-11-20

## Context

The infrastructure adapters, the primary adapters and the composition root
need to report what happens at run time: requests served, files ingested, calls
to FFmpeg and ExifTool that fail, errors nobody expected. Javalin and Jetty
(ADR 0014) already write their own logs through the SLF4J API, so a server
without an SLF4J provider loses them and prints a warning at startup.

The domain must stay free of libraries (ADR 0013): it reports what happened
through domain events and exceptions, not through logs.

## Decision

- Code outside the domain logs through the **SLF4J 2 API** (`org.slf4j:slf4j-api`).
  The infrastructure and presentation modules depend only on that API.
- **Logback** (`ch.qos.logback:logback-classic`, 1.5) is the provider. Only the
  bootstrap module depends on it, so it can be replaced without touching the
  adapters.
- Both versions are declared once in the parent POM's `dependencyManagement`,
  so the dependency convergence rule keeps them aligned with the SLF4J that
  Javalin and ArchUnit bring.
- The domain does not log. The Maven rule that bans dependencies in the domain
  (ADR 0013) already keeps SLF4J out of its production code.
- `src/bootstrap/src/main/resources/logback.xml` configures the output:
  - console, always;
  - a rolling file in `<home>/logs/` (ADR 0022), in both profiles: the desktop
    app has no console anyone watches, and a server keeps its logs in the same
    volume as its data. Logback cannot switch appenders by profile without an
    extra library (Janino), so it does not try;
  - level `INFO` by default; the `OMNISTREAM_LOG_LEVEL` environment variable
    changes the level of the `co.omnistream` loggers.
- What to log:
  - each HTTP request, with method, path, status and time, at `INFO`;
  - domain exceptions, which are expected outcomes, at `DEBUG`, without a
    stack trace;
  - any other exception, with its stack trace, at `ERROR`;
  - startup: profile, port and paths in use, at `INFO`.
- Messages use SLF4J placeholders (`log.info("Indexed {}", id)`), never string
  concatenation.
- Passwords, session tokens and shared link tokens are never logged, not even
  at `DEBUG`.

## Consequences

- The logs of Javalin, Jetty and our adapters end up in the same place, with the
  same format.
- Changing the provider means changing one dependency and one file in the
  bootstrap module.
- Each module that logs adds `slf4j-api` to its POM.
- Tests run without a provider, so SLF4J prints a three-line warning; it is
  harmless.
