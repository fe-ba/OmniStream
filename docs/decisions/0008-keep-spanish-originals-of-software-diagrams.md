# 0008. Keep Spanish originals of the software diagrams

- **Status:** Accepted
- **Date:** 2026-11-19

## Context

ADR 0006 requires code and repository documentation in English, with one
exception: the enterprise architecture poster stays in Spanish because it is
the deliverable for the course.

The software architecture diagrams (use cases, components, domain model,
services, ports and adapters) are also a course deliverable and were designed
and reviewed in Spanish. At the same time, their class diagrams name the Java
classes, methods and attributes one to one. If the code is in English, a
Spanish diagram no longer matches it.

## Decision

We will keep both versions of the software diagrams in the repository:

- The **English versions** live in `docs/architecture/software/`. They are the
  reference for the code: class, method, attribute and enum names in the code
  are exactly the ones in these diagrams.
- The **Spanish originals** live in `docs/architecture/software/es/`. They are
  the course deliverable and keep their original file names.
- Both versions describe exactly the same design. Every Spanish identifier
  maps to one English identifier through the glossary in
  `docs/reference/glossary.md`, which also lists the few terms whose
  translation depends on the context.
- A change to the design is made in both versions in the same commit. A new
  term is added to the glossary in that commit too.

This extends the exception of ADR 0006 to these diagrams. Everything else
(code, other documentation, configuration) stays in English.

## Consequences

- The code and the English diagrams use the same names, and the course keeps
  its Spanish deliverable.
- Each diagram is maintained twice. A change made to only one version
  leaves them out of sync; reviewers must check that a pull request touching
  `docs/architecture/software/` changes both.
- The glossary becomes the single source for names: when the code needs a
  name that is not in the diagrams, it is added to the glossary first.
- Both versions are rendered to SVG by CI (ADR 0007), so either can be viewed
  on GitHub.
