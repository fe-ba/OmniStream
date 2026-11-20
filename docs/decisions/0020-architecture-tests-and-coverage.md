# 0020. Architecture tests and coverage

- **Status:** Accepted
- **Date:** 2026-11-20

## Context

The modules enforce the dependency rule between layers (ADR 0013), but most of
the design lives inside the domain module: the package structure (ADR 0019),
aggregates that refer to each other by identifier and expose their state only
through snapshots (ADR 0011), and the coding conventions (no setters, no clock
in the domain). Three people write that code at the same time, and a review can
miss a broken rule.

The team also has no measure of how much of the code its tests exercise.

## Decision

- **ArchUnit** (`com.tngtech.archunit:archunit`, test scope, domain module)
  checks the architecture of the domain in `ArchitectureTest`, as plain JUnit
  tests that run with `./mvnw verify`:
  - the model, events, snapshots, exceptions and policies do not depend on
    services or ports, and ports do not depend on services;
  - ports are interfaces, and every domain service implements a driving port;
  - aggregates refer to other aggregates only by identifier;
  - snapshots are records, and domain exceptions extend `DomainException`;
  - entities have no setters, the domain never reads the clock and never uses
    JDBC.
- **JaCoCo** (`jacoco-maven-plugin`) measures coverage on every build. Each
  module writes its report to `target/site/jacoco/`; CI shows line and branch
  coverage per module on the run's summary page and keeps the reports as an
  artifact for 14 days.
- There is no minimum coverage yet. Once the domain is complete, the team can
  add one with the plugin's `check` goal.
- Package cycles inside the domain are allowed: the model, its events, its
  snapshots and the deduplication policy refer to each other by design. The
  rules check the direction that matters, from the core outwards.

## Consequences

- Breaking a design decision fails the build with a message that names the
  rule and the reason, instead of waiting for a review.
- Changing a rule means changing `ArchitectureTest`, which shows up in the diff
  and is discussed like any other change.
- Coverage is visible on every build, which is useful evidence for the course.
- Builds take a little longer: ArchUnit imports the domain classes and JaCoCo
  instruments the tests.
