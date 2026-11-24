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
| Implementation (Java)    | Done        | `src/`                           |

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
[coding conventions](docs/reference/coding-conventions.md); the design patterns
and where each one is are in [docs/patrones.md](docs/patrones.md).

## Running OmniStream

Build the application into one file, from `src/`:

```bash
./mvnw -DskipTests package
java -jar bootstrap/target/omnistream.jar
```

It serves the web UI and the REST API on <http://localhost:8080>. On the first
start it creates the administrator `admin` and prints its password on the
console, only that once (ADR 0029): write it down, log in, and create the other
accounts on the People screen. Ctrl+C stops it.

Everything lives in the home folder, `~/.omnistream` (or `OMNISTREAM_HOME`):
the library in `library/`, the SQLite database `omnistream.db`, the backups,
the transcoding cache and the logs. To change a setting, write it in
`~/.omnistream/omnistream.properties`, pass another file with `--config
<file>`, or set the environment variable (`http.port` is
`OMNISTREAM_HTTP_PORT`); the keys are in
[ADR 0022](docs/decisions/0022-configuration-from-properties-and-environment.md):

```properties
http.port=9090
library.path=/home/me/Media
```

With arguments, the same jar runs one command instead of the server:

```bash
java -jar bootstrap/target/omnistream.jar help
java -jar bootstrap/target/omnistream.jar ingest ~/Music/song.mp3
java -jar bootstrap/target/omnistream.jar status
```

FFmpeg and ExifTool are optional: without them files still enter the
catalog and play when the browser can play them, but have no extracted
metadata and are not transcoded. The `server` profile (`profile=server`) keeps
the catalog in PostgreSQL instead of SQLite and needs `database.url`, plus
`OMNISTREAM_DATABASE_USER` and `OMNISTREAM_DATABASE_PASSWORD` in the environment.

To try the web UI with sample data and nothing installed, run the in-memory
demo instead; its users are `admin`, `ana` and `luis`, with the username as
password:

```bash
./mvnw -pl presentation -am test -Ddemo=true -Dtest=DemoServer -Dsurefire.failIfNoSpecifiedTests=false
```

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
