# 0013. One Maven module per layer

- **Status:** Accepted
- **Date:** 2026-11-19

## Context

The architecture has three layers (presentation, domain and infrastructure)
plus a composition root that is not a layer. Its central rule is that
dependencies point inwards: the domain depends on nothing, and presentation and
infrastructure depend only on the domain. In a single module, nothing stops a
domain class from importing a database or HTTP class by mistake.

## Decision

The Java code in `src/` is a multi-module Maven project, with one module per
layer and one for bootstrapping:

| Module | Folder | Contents | Depends on |
|---|---|---|---|
| `omnistream-domain` | `src/domain` | Model, events, policies, snapshots, domain services, driving and driven ports, port DTOs | nothing (only the JDK) |
| `omnistream-infrastructure` | `src/infrastructure` | Secondary adapters, mappers, decorators | domain |
| `omnistream-presentation` | `src/presentation` | Primary adapters: REST API, web UI, CLI, scheduler and tasks | domain |
| `omnistream-bootstrap` | `src/bootstrap` | Composition root, infrastructure factories, configuration, `main` | all of the above |

The parent `pom.xml` lives in `src/` and manages versions for all modules. The
base package is `co.omnistream`, with one subpackage per module
(`co.omnistream.domain`, `co.omnistream.infrastructure`, ...).

## Consequences

- The compiler enforces the dependency rule: a domain class that imports
  infrastructure code does not compile, because that module is not on its
  classpath.
- Presentation and infrastructure cannot see each other; only bootstrap knows
  both, which is exactly the role of the composition root.
- The domain can be built and tested on its own, with no database or server.
- Four `pom.xml` files plus the parent take more setup than one module, and a
  class in the wrong module is caught at compile time, not at review time.
