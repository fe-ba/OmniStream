# 0006. English and Conventional Commits

- **Status:** Accepted
- **Date:** 2026-11-05

## Context

Early commits, configuration comments and tool messages mixed Spanish and
English. The code will be written in Java, whose ecosystem and tooling are in
English, and a consistent history is easier to read and search.

## Decision

We will write commit messages, branch names, repository configuration (hooks,
workflows, `.gitignore`, `.gitattributes`), code and repository documentation in
English. Commit messages follow Conventional Commits: `type: description`, in
the imperative, with types such as `feat`, `fix`, `docs`, `chore`, `refactor`
and `test`.

The content of the enterprise architecture poster stays in Spanish, since it is
the deliverable for the course.

## Consequences

- The history is uniform and easy to scan by type.
- Code, docs and tooling share one language.
- Team members need to write in English, even for quick commits.
- Existing commits from before this decision stay as they are.
