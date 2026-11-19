# 0012. Java 25 and Maven

- **Status:** Accepted
- **Date:** 2026-11-19

## Context

ADR 0006 states that the code is written in Java, but not which version or how
it is built. The team needs a build that works the same on every computer and
in CI, with as little to install as possible, and that supports several
modules (see ADR 0013).

Java 25 is the current long-term support release (September 2025). Java 21 is
the previous one. Both have everything the design uses: records for snapshots
and value objects, sealed types, pattern matching and virtual threads.

## Decision

- The code targets **Java 25**: the compiler is configured with `release 25`.
- The build uses **Maven**, through the **Maven Wrapper** (`mvnw`) committed to
  the repository, so only a JDK has to be installed.
- Tests use **JUnit Jupiter** and **AssertJ**.
- CI builds the project and runs the tests on every push and pull request that
  touches `src/`, with `./mvnw verify`.

## Consequences

- Everybody builds with the same Maven version, pinned by the wrapper.
- Each team member needs a Java 25 JDK. Any distribution works (Temurin,
  OpenJDK, Oracle).
- Maven's conventions (standard folder layout, declarative `pom.xml`) leave
  little to configure, but anything unusual in the build takes more XML than in
  Gradle.
- The next LTS is planned for 2027; moving to it later is a one-line change.
