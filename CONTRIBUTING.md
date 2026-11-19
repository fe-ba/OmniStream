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

Each area has an owner (ADR 0019, enforced with [`CODEOWNERS`](.github/CODEOWNERS)).
GitHub asks the owner to review any pull request that touches their area.

| Area | Packages | Owner |
|---|---|---|
| Domain model | `domain.model.*`, `domain.event`, `domain.snapshot`, `domain.exception`, `domain.policy` | @fe-ba |
| Ports and adapters | `domain.port.*`, in-memory implementations and contracts, `omnistream-infrastructure` | @HelberthGM |
| Domain services | `domain.service.*` | @YoshiK13 |
| Build, CI and documentation | `src/pom.xml`, `.github/`, `docs/` | @fe-ba |

Presentation and bootstrap get an owner when their phase starts; until then
they fall under @fe-ba.

## Workflow

Nobody pushes to `main` directly. Every change goes through a pull request.

1. **Pick an issue** from the 1.0 milestone and assign it to yourself.
2. **Create a branch from an up-to-date `main`**:
   ```bash
   git switch main
   git pull
   git switch -c feat/model-catalog
   ```
   Branch names are `<type>/<area>-<topic>`: `feat/model-catalog`,
   `test/ports-in-memory`, `fix/services-ingestion`.
3. **Commit in small steps**, with [Conventional Commits](https://www.conventionalcommits.org/)
   in English (ADR 0006) and the area as scope:
   `feat(model): add PlaybackSession`, `test(ports): add session repository contract`.
   Scopes: `model`, `ports`, `infra`, `services`, `presentation`,
   `bootstrap`, `build`, `ci`, `docs`, `diagrams`.
4. **Before pushing**, from `src/`:
   ```bash
   ./mvnw spotless:apply
   ./mvnw verify
   ```
5. **Open a pull request to `main`.** Its title follows Conventional Commits,
   because it becomes the commit message after the squash. Fill in the
   template and link the issue (`Closes #12`).
6. **Review.** At least one other member approves. If the pull request touches
   someone else's area, that owner approves. Answer every comment before
   merging.
7. **Merge** with *Squash and merge* once the *Build* check is green, and
   delete the branch.

If `main` moves while you work, bring the changes into your branch with
`git pull --rebase origin main` and fix any conflicts before pushing again
(`git push --force-with-lease` after a rebase).

## Changing a contract

The signatures in the diagrams are the contract between the three areas: a
service relies on the methods of the model and the ports exactly as drawn. To
change one (a port method, a public method of an entity, a snapshot field):

1. Explain the change in the pull request and ask the owners it affects.
2. Update, in the same pull request:
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
