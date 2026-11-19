# Contributing to OmniStream

How the team works on this repository. The rules for the code itself are in
[`docs/reference/coding-conventions.md`](docs/reference/coding-conventions.md).

## Set up your computer

1. **JDK 25** (ADR 0012). Any distribution works:
   - Arch Linux: `sudo pacman -S jdk25-openjdk` and
     `sudo archlinux-java set java-25-openjdk`.
   - Windows, macOS or other Linux: Eclipse Temurin 25 from
     [adoptium.net](https://adoptium.net/), or `sdk install java 25-tem` with
     SDKMAN.
   - Check it with `java -version`.
2. **Maven is not needed**: the wrapper (`src/mvnw`) downloads the right
   version the first time.
3. **Clone and configure git** once:
   ```bash
   git clone git@github.com:fe-ba/OmniStream.git
   cd OmniStream
   git config core.hooksPath .githooks
   git config merge.ours.driver true
   ```
4. **Check that everything builds**: `cd src && ./mvnw verify` must end in
   `BUILD SUCCESS`.
5. **IDE**. In IntelliJ IDEA, open `src/pom.xml` as a project, set the project
   SDK to 25 and install the *google-java-format* plugin (enable it in
   Settings, google-java-format Settings). VS Code with the Java extension
   pack also works; run `./mvnw spotless:apply` before committing.

## Who owns what

Each area has an owner (ADR 0019, also listed in [`CODEOWNERS`](.github/CODEOWNERS),
so GitHub asks the owner to review any pull request that touches their area).

| Area | Packages | Owner |
|---|---|---|
| Domain model | `domain.model.*`, `domain.event`, `domain.snapshot`, `domain.exception`, `domain.policy` | @fe-ba |
| Ports and adapters | `domain.port.*`, in-memory implementations and contracts, `omnistream-infrastructure` | @HelberthGM |
| Domain services | `domain.service.*` | @YoshiK13 |
| Build, CI and documentation | `src/pom.xml`, `.github/`, `docs/` | @fe-ba |

Presentation and bootstrap get an owner when their phase starts; until then
they fall under @fe-ba.

## Branches

| Branch | For | Owner |
|---|---|---|
| `main` | Integrated code that compiles and passes every test | everybody |
| `model` | Domain model work | @fe-ba |
| `ports` | Ports, in-memory implementations, contracts and adapters | @HelberthGM |
| `services` | Domain services | @YoshiK13 |

`main` is not protected: anyone may push to it, as long as the code passes
`./mvnw verify`. Day-to-day work happens on your area branch, so the three of
you do not step on each other.

## Workflow

1. **Work on your area branch** and commit as often as you like, with
   [Conventional Commits](https://www.conventionalcommits.org/) in English
   (ADR 0006) and the area as scope: `feat(model): add PlaybackSession`,
   `test(ports): add session repository contract`. Scopes: `model`, `ports`,
   `infra`, `services`, `presentation`, `bootstrap`, `build`, `ci`, `docs`,
   `diagrams`.
   ```bash
   git switch model
   git pull
   ```
2. **Bring `main` into your branch often** (every day you work, and always
   before integrating), so conflicts stay small:
   ```bash
   git fetch
   git merge origin/main
   ```
3. **Integrate into `main`** when a piece is finished and tested, at least
   every few days. From `src/`, `./mvnw spotless:apply` and `./mvnw verify`
   must pass on your branch; then:
   ```bash
   git switch main
   git pull
   git merge model
   cd src && ./mvnw verify && cd ..
   git push
   git switch model
   ```
   If you want a review first, open a pull request from your branch to `main`
   instead; GitHub asks the owners of the touched areas automatically
   ([`CODEOWNERS`](.github/CODEOWNERS)).
4. **Never rewrite shared history**: no `rebase` or `push --force` on `main`
   or on the area branches. Use `merge`.

**Stay in your area.** Do not edit another area's packages on your branch. If
you need something from another area (a new port method, a field in a
snapshot), ask its owner. Small agreed changes can go straight to `main`, so
everybody gets them with their next merge.

## Changing a contract

The signatures in the diagrams are the contract between the three areas: a
service relies on the methods of the model and the ports exactly as drawn. To
change one (a port method, a public method of an entity, a snapshot field):

1. Agree on it with the owners it affects before writing it.
2. Update, in the same commit or pull request:
   - the English diagram in `docs/architecture/software/`;
   - its Spanish original in `docs/architecture/software/es/` (ADR 0008);
   - the [glossary](docs/reference/glossary.md) if a name changes.
3. If it changes a design decision, add an ADR in `docs/decisions/`.

## Generated files

Do not edit or commit these by hand; CI regenerates them:

- the SVGs of the software diagrams (from their `.puml`);
- the poster PDF, `docs/architecture/empresarial/main.pdf`.

## Language

Code, commits, branches, pull requests and repository documentation are in
English. The enterprise architecture poster and the Spanish originals of the
diagrams stay in Spanish.
