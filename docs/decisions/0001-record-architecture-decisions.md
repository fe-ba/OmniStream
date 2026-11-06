# 0001. Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-11-05

## Context

The project goes from enterprise architecture to software architecture to
code, and is built by a team over a whole semester. Decisions made early (tools,
structure, conventions) shape later work, and the reasons behind them are easy
to forget or lose in chat history.

## Decision

We will record significant decisions as Architecture Decision Records in
`docs/decisions/`, using the format described by Michael Nygard: context,
decision and consequences. Each ADR is a numbered Markdown file, and an ADR is
never rewritten once accepted; a new ADR supersedes it instead.

## Consequences

- Anyone joining or reviewing the project can see why things are the way they are.
- The ADRs double as evidence of the design process for the course.
- Writing an ADR takes a few minutes per decision; trivial choices don't need one.
