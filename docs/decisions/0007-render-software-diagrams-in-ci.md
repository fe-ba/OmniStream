# 0007. Render software diagrams in CI

- **Status:** Accepted
- **Date:** 2026-11-19

## Context

The software architecture diagrams (use cases, components, domain model,
services, ports and adapters) are written in PlantUML. Their `.puml` sources
live in `docs/architecture/software/`: the English versions at the top level,
following ADR 0006, and the Spanish originals, which are the course
deliverable, in `es/`.

A `.puml` file is plain text, so it can be versioned, diffed and reviewed like
code, but reading it is not the same as seeing the diagram. Rendering it
requires Java, PlantUML and Graphviz, which not every team member has
installed, and an image rendered by hand easily falls out of date with its
source. Reviewers, including the professor, should be able to see the current
diagrams directly on GitHub without installing anything.

## Decision

We will version each diagram as its `.puml` source plus an `.svg` rendered from
it, side by side and with the same name, and we will generate the SVGs only in
CI:

- The `diagrams.yml` workflow runs on pushes and pull requests that touch a
  `.puml` file under `docs/architecture/software/`, and can also be run by hand.
- It uses a pinned PlantUML version, verified by checksum, so every run renders
  the same way.
- Each diagram is rendered on its own and saved as `<file name>.svg`, whatever
  name its `@startuml` line declares. SVGs whose `.puml` no longer exists are
  deleted.
- If any diagram fails to render, the workflow fails and nothing is committed.
- On pushes to `main`, the SVGs are committed back with
  `docs: render software diagrams [skip ci]`. On pull requests they are only
  checked and uploaded as an artifact.
- SVGs under `docs/architecture/software/` are marked `merge=ours` in
  `.gitattributes`: a conflict on them is resolved automatically and the next
  run regenerates them.

Nobody edits or commits these SVGs by hand. To change a diagram, edit its
`.puml` and push.

## Consequences

- The SVG in the repository always matches its source and can be viewed on
  GitHub.
- Contributors only need a text editor. A local preview (for example the
  PlantUML extension for VS Code) is optional.
- Every push that changes a diagram is followed by a commit from CI, so
  contributors have to `git pull` before their next commit, as the README
  already asks.
- The SVGs are rendered with the fonts available on the CI runner, so they can
  look slightly different from a local preview.
- Unlike the poster diagrams (ADR 0004), which are exported from Archi and
  committed by hand, these SVGs are build outputs.
- Each clone needs `git config merge.ours.driver true`, the same setting that
  ADR 0005 already requires.
