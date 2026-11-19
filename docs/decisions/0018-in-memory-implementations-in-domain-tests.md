# 0018. In-memory implementations in the domain tests

- **Status:** Accepted
- **Date:** 2026-11-19

## Context

Every driven port needs two implementations: the real one, and one in memory
so that the domain can be tested without a database, a disk or external tools.

The tests of the domain services live in `omnistream-domain` and need the
in-memory implementations. If those implementations were in
`omnistream-infrastructure`, the domain would depend on infrastructure for its
tests. That breaks the dependency rule (ADR 0013), and Maven rejects the cycle
even when the dependency is only for tests.

Two implementations of the same port can also drift apart: a test that passes
with the in-memory version says nothing about the real one unless both are
held to the same behaviour.

## Decision

- The in-memory implementations live in the test sources of the domain
  module, package `co.omnistream.domain.port.out.memory`, named
  `InMemory<Port>` (for example `InMemoryCatalogRepository`).
- Every driven port has one, including the ports whose real implementation is
  also in memory (tokens and event bus). For the ports backed by external
  tools (`IMetadataExtractor`, `ITranscoder`, `IIdentityProvider`) the test
  version returns fixed, configurable data.
- Ports with behaviour worth specifying have a **contract test**: an abstract
  JUnit class `<Port>Contract` in the same test sources, with an abstract
  factory method that returns the implementation under test. This covers the
  repositories, the change log, the outbox, the configuration repository, file
  storage, tokens and the event bus.
- The domain module publishes its test sources as a test-jar. The
  infrastructure module depends on it with `test` scope, and each real adapter
  has a test that extends the same contract.
- The real implementations stay only in `omnistream-infrastructure`.

## Consequences

- The domain services are tested with no infrastructure at all, and the
  dependency rule holds in the build.
- In-memory and real implementations pass the same tests, so a service tested
  with the in-memory version behaves the same with the real one.
- The ports and adapters work starts with the in-memory implementations and
  the contracts. The services work can use them as soon as they exist, without
  waiting for JDBC.
- A contract test has to be written before the first implementation of each
  port, and changing a port means changing its contract too.
- The test-jar is one more artifact to configure in the POMs.
