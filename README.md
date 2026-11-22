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

## Running the project

The application is currently run through its demo server. It includes the web
UI, REST API and in-memory sample data, so it does not require a database,
FFmpeg or ExifTool.

Requirements:

- JDK 25. Check the installed version with `java -version`.
- No system Maven installation is needed; `src/mvnw` downloads and uses the
  Maven version required by the project.

### 1. Build and test

From the repository root:

```bash
cd src
./mvnw verify
```

It must finish with `BUILD SUCCESS`. This runs the format check, architecture
rules, compilation and all tests.

### 2. Start the demo

From `src/`:

```bash
./mvnw -pl presentation -am test \
  -Ddemo=true \
  -Dtest=DemoServer \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Keep the terminal open. Once it prints the OmniStream address, open
[http://localhost:7070](http://localhost:7070).

The default demo users are:

| Username | Password |
|---|---|
| `admin` | `admin` |
| `ana` | `ana` |
| `luis` | `luis` |

To use another port:

```bash
./mvnw -pl presentation -am test \
  -Ddemo=true \
  -Ddemo.port=8080 \
  -Dtest=DemoServer \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Then open [http://localhost:8080](http://localhost:8080). Stop the server with
`Ctrl+C`.

### Troubleshooting

- If Maven reports that Java 25 or newer is required, install JDK 25 or configure
  `JAVA_HOME` to point to it.
- If port `7070` is already in use, start the demo with a different
  `-Ddemo.port=...` value.
- If the demo does not start, run `./mvnw verify` first and fix the reported
  compilation or test failure.

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

- Each area has its own branch (`model`, `ports`, `services`); integrate into
  `main` often, and only with code that passes `./mvnw verify`.
- Commits, branches and repository files are written in English, following
  [Conventional Commits](https://www.conventionalcommits.org/):
  `feat(model): ...`, `fix(services): ...`, `docs: ...`.
- Significant decisions are recorded as ADRs in [`docs/decisions/`](docs/decisions/).
