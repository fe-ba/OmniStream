# 0002. Repository structure

- **Status:** Accepted
- **Date:** 2026-11-05

## Context

The repository started as a LaTeX poster with all its files at the root. The
project will also contain the software architecture, its documentation and the
Java implementation, so a flat layout would mix unrelated files.

## Decision

We will split the repository into `src/` for source code and `docs/` for
documentation. `docs/` is divided by the kind of document, loosely following
Diátaxis:

- `architecture/`: architecture models; the enterprise architecture poster
  lives in `architecture/empresarial/` as a self-contained LaTeX project.
- `reference/`: technical reference (data model, APIs, glossary).
- `explanation/`: background and design rationale.
- `decisions/`: ADRs.

Git hooks stay in `.githooks/` and workflows in `.github/workflows/` at the root,
where git and GitHub expect them.

## Consequences

- Each kind of content has an obvious place, and code and documentation can
  evolve independently.
- The poster keeps working unchanged because all its paths are relative to its
  own folder.
- Hooks and CI must point to the poster folder explicitly, and only run when it
  changes.
- Overleaf has to be configured with `docs/architecture/empresarial/main.tex` as
  the main document.
