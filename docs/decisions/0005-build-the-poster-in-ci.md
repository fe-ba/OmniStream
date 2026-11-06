# 0005. Build the poster in CI

- **Status:** Accepted
- **Date:** 2026-11-05

## Context

Not every team member has a working LaTeX setup, some edit through Overleaf,
and a broken commit blocks everyone. The final PDF must always match the
sources in `main`.

## Decision

We will build the poster in two places:

- **Locally**, a `pre-commit` hook in `.githooks/` compiles the poster and aborts
  the commit if it fails. It only runs when the commit touches `.tex`, `.bib`,
  `.svg` or `.latexmkrc` files, and skips itself if `latexmk` is not installed.
- **In CI**, the `poster.yml` workflow builds the poster on pushes and pull
  requests that touch the same files, and on pushes to `main` commits the result
  as `main.pdf`.

`main.pdf` is marked `merge=ours` in `.gitattributes`, so conflicts on it are
resolved automatically; the next CI run regenerates it anyway.

## Consequences

- `main.pdf` in the repository always reflects the latest sources.
- Contributors only commit sources; nobody needs to commit the PDF by hand.
- Commits to code or other docs are not slowed down by LaTeX builds.
- Each clone must run `git config core.hooksPath .githooks` and
  `git config merge.ours.driver true` once.
- Overleaf does not run the hook, so validation for those edits comes only from CI.
