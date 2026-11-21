# Architecture Decision Records

An ADR records one significant decision: the context that led to it, what was
decided and its consequences. ADRs are never deleted or rewritten; when a
decision changes, a new ADR supersedes the old one.

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-repository-structure.md) | Repository structure | Accepted |
| [0003](0003-latex-with-lualatex-for-the-poster.md) | LaTeX with LuaLaTeX for the poster | Accepted |
| [0004](0004-version-diagrams-as-svg.md) | Version diagrams as SVG | Accepted |
| [0005](0005-build-the-poster-in-ci.md) | Build the poster in CI | Accepted |
| [0006](0006-english-and-conventional-commits.md) | English and Conventional Commits | Accepted |
| [0007](0007-render-software-diagrams-in-ci.md) | Render software diagrams in CI | Accepted |
| [0008](0008-keep-spanish-originals-of-software-diagrams.md) | Keep Spanish originals of the software diagrams | Accepted |
| [0009](0009-scope-of-version-1-0.md) | Scope of version 1.0 | Accepted |
| [0010](0010-desktop-app-is-the-local-server.md) | Desktop app is the server in local mode | Accepted |
| [0011](0011-snapshots-to-expose-aggregate-state.md) | Snapshots to expose aggregate state | Accepted |
| [0012](0012-java-25-and-maven.md) | Java 25 and Maven | Accepted |
| [0013](0013-one-maven-module-per-layer.md) | One Maven module per layer | Accepted |
| [0014](0014-javalin-for-http.md) | Javalin for HTTP | Accepted |
| [0015](0015-plain-jdbc-for-persistence.md) | Plain JDBC for persistence | Accepted |
| [0016](0016-automatic-classification.md) | Automatic classification | Accepted |
| [0017](0017-docker-image-after-version-1-0.md) | Docker image after version 1.0 | Accepted |
| [0018](0018-in-memory-implementations-in-domain-tests.md) | In-memory implementations in the domain tests | Accepted |
| [0019](0019-package-structure.md) | Package structure | Accepted |
| [0020](0020-architecture-tests-and-coverage.md) | Architecture tests and coverage | Accepted |
| [0021](0021-logging-with-slf4j-and-logback.md) | Logging with SLF4J and Logback | Accepted |
| [0022](0022-configuration-from-properties-and-environment.md) | Configuration from a properties file and the environment | Accepted |
| [0023](0023-rest-api-contract.md) | REST API contract | Accepted |
| [0024](0024-delegate-permissions-per-collection.md) | Delegate permissions per collection | Accepted |
| [0025](0025-audit-trail-with-decorators.md) | Audit trail with decorators | Accepted |
| [0026](0026-live-catalog-changes.md) | Live catalog changes over server-sent events | Accepted |
| [0027](0027-collaborative-text-editing.md) | Collaborative editing of text files | Accepted (implementation pending) |

## Adding an ADR

1. Copy [`template.md`](template.md) to `NNNN-short-title.md`, using the next number.
2. Fill it in and set its status to `Proposed`.
3. Once the team agrees, set it to `Accepted` and add it to the index above.
