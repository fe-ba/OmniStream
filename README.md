# OmniStream

OmniStream is a self-hosted platform that lets people and organizations store,
categorize, search and play their multimedia files autonomously, securely and
mostly offline, while keeping full control of their information.

This repository holds the semester project for the course *Arquitectura de
Software y Patrones*: from the enterprise architecture, through the software
architecture, to the implementation.

## Team

- Helberth Alejandro Garcia Melo
- Joshoa Alarcón Sánchez
- Ferney Eduardo Ballesta Avila

## Status

| Stage                    | Status      | Location                         |
|--------------------------|-------------|----------------------------------|
| Enterprise architecture  | Done        | `docs/architecture/empresarial/` |
| Software architecture    | Not started | `docs/architecture/`             |
| Implementation (Java)    | Not started | `src/`                           |

## Repository structure

```
.
├── README.md
├── src/                         application source code
├── docs/
│   ├── architecture/
│   │   └── empresarial/         enterprise architecture poster (LaTeX + ArchiMate)
│   ├── reference/               technical reference: data model, APIs, glossary
│   ├── explanation/             background and design rationale
│   └── decisions/               architecture decision records (ADRs)
├── .githooks/                   local git hooks
└── .github/workflows/           CI pipelines
```

## Enterprise architecture poster

The poster is written in LaTeX and its diagrams are modeled in Archi
(ArchiMate). CI builds it on every push that touches it and commits the result
as [`main.pdf`](docs/architecture/empresarial/main.pdf).

To build it locally, from `docs/architecture/empresarial/`:

```bash
latexmk -pdflua main.tex
```

Requirements, Overleaf setup and how to add diagrams are described in the
[poster README](docs/architecture/empresarial/README.md).

## Getting started

Run once after cloning:

```bash
git config core.hooksPath .githooks      # build the poster before each commit that changes it
git config merge.ours.driver true        # auto-resolve conflicts on the CI-generated main.pdf
```

## Contributing

- Always `git pull` before you start working.
- Commits, branches and repository files are written in English, following
  [Conventional Commits](https://www.conventionalcommits.org/):
  `feat: ...`, `fix: ...`, `docs: ...`, `chore: ...`.
- Significant decisions are recorded as ADRs in [`docs/decisions/`](docs/decisions/).
