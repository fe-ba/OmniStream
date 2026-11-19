# OmniStream

OmniStream is a self-hosted platform that lets people and organizations store,
categorize, search and play their multimedia files autonomously, securely and
mostly offline, while keeping full control of their information.

This repository holds the semester project for the course *Arquitectura de
Software y Patrones*: from the enterprise architecture, through the software
architecture, to the implementation.

## Team

- Helberth Alejandro Garcia Melo ([@HelberthGM](https://github.com/HelberthGM)): ports and adapters
- Joshoa Alarcón Sánchez ([@YoshiK13](https://github.com/YoshiK13)): domain services
- Ferney Eduardo Ballesta Avila ([@fe-ba](https://github.com/fe-ba)): domain model

## Status

| Stage                    | Status      | Location                         |
|--------------------------|-------------|----------------------------------|
| Enterprise architecture  | Done        | `docs/architecture/empresarial/` |
| Software architecture    | Done        | `docs/architecture/software/`    |
| Implementation (Java)    | In progress | `src/`                           |

## Repository structure

```
.
├── README.md
├── CONTRIBUTING.md              how the team works: setup, branches, reviews
├── src/                         Java code: Maven project, one module per layer
│   ├── domain/                  model, events, snapshots, services, ports
│   ├── infrastructure/          secondary adapters (persistence, files, tools)
│   ├── presentation/            primary adapters (REST API, web UI, CLI)
│   └── bootstrap/               composition root and configuration
├── docs/
│   ├── architecture/
│   │   ├── empresarial/         enterprise architecture poster (LaTeX + ArchiMate)
│   │   └── software/            software diagrams (PlantUML), Spanish originals in es/
│   ├── reference/               glossary and coding conventions
│   ├── explanation/             background and design rationale
│   └── decisions/               architecture decision records (ADRs)
├── .githooks/                   local git hooks
└── .github/                     CI pipelines, code owners, pull request template
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

## Building the code

Requires JDK 25; Maven comes with the wrapper. From `src/`:

```bash
./mvnw verify            # format check, layer rules, compile and test
./mvnw spotless:apply    # format the code
```

The design behind the code is in the
[software diagrams](docs/architecture/software/) and the
[ADRs](docs/decisions/); the rules for writing it are in the
[coding conventions](docs/reference/coding-conventions.md).

## Getting started

Run once after cloning:

```bash
git config core.hooksPath .githooks      # build the poster before each commit that changes it
git config merge.ours.driver true        # auto-resolve conflicts on the CI-generated main.pdf
```

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before your first pull request. In
short:

- Every change goes through a pull request to `main`, reviewed by the owner of
  the area it touches.
- Commits, branches and repository files are written in English, following
  [Conventional Commits](https://www.conventionalcommits.org/):
  `feat(model): ...`, `fix(services): ...`, `docs: ...`.
- Significant decisions are recorded as ADRs in [`docs/decisions/`](docs/decisions/).
