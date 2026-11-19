# 0009. Scope of version 1.0

- **Status:** Accepted
- **Date:** 2026-11-19

## Context

The software architecture describes more than one semester of work: five
clients (web, desktop, mobile, CLI and REST API), two databases (SQLite and
PostgreSQL), two identity providers (local and OIDC) and two kinds of file
storage (local disk and network shares). The enterprise architecture already
plans for this with three plateaus (OmniStream 1.0, 2.0 and 3.0).

Version 1.0 has to be finished within the semester, but it must still show the
architecture and the GoF patterns of the design working in code, not only on
paper. Several patterns need a second implementation to be meaningful: the
Abstract Factory needs two factories and the persistence Template Method needs
two databases.

## Decision

Version 1.0 includes:

- The whole domain: the four aggregates, domain events, policies and every
  domain service, including the ingestion chain, the stream strategies and the
  sync observer.
- Primary adapters: REST API, CLI, a simple web UI served by the same server,
  and the scheduler with its tasks.
- Secondary adapters:
  - JDBC persistence with **SQLite and PostgreSQL**;
  - local file storage;
  - local identity;
  - metadata extraction (FFprobe / ExifTool);
  - transcoding (FFmpeg);
  - in-memory tokens;
  - in-memory event bus.
- Decorators: cached catalog repository and cached transcoder.
- Both infrastructure factories. In 1.0 the server factory differs from the
  local one only in the database (PostgreSQL); it uses local file storage and
  local identity until their network and OIDC versions exist.

Out of scope for 1.0, kept in the design for later plateaus:

- the OIDC identity adapter and the resilient identity provider that wraps it;
- network file storage (NAS / SMB);
- the mobile client and its adapter;
- a separate desktop client (see ADR 0010).

## Consequences

- Every pattern in the design except the resilient identity decorator has
  running code in 1.0.
- The diagrams keep the full design. The parts left out are marked as such
  when the diagrams are next updated, so the scope is visible in them.
- Adding OIDC or network storage later means writing one adapter and changing
  one factory; no domain code changes.
- Supporting PostgreSQL from the start forces the SQL to stay portable between
  the two databases (see ADR 0015).
