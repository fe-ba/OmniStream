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

## Adding an ADR

1. Copy [`template.md`](template.md) to `NNNN-short-title.md`, using the next number.
2. Fill it in and set its status to `Proposed`.
3. Once the team agrees, set it to `Accepted` and add it to the index above.
