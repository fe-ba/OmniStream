# 0025. Audit trail with decorators

- **Status:** Accepted
- **Date:** 2026-11-21

## Context

In a company someone has to be able to answer who changed what, and when: who
deleted a file, who gave Luis access to a collection, who tried to log in to an
account with the wrong password. Filtered by file or collection, the same
records are the history of that item, which users ask for too.

The changes happen in the domain services, which are already written as stubs
with implementation notes (ADR 0019). Making every service also write the trail
would add a dependency and a line to each of their methods, mix two concerns,
and make it easy to forget one.

## Decision

We will keep an append-only audit trail, written by decorators around the
driving ports:

- An `AuditEntry` records who (`actor`), what (`AuditAction`), when, on which
  file or collection (`resource`, optional), to whom (`subject`, optional) and a
  short `detail`. Entries are values: written once, never changed or deleted.
- The driven port `IAuditLog` only appends and finds. The persistence adapter
  stores the trail in its own table.
- Four decorators (Decorator pattern) implement `IAccessControl`,
  `ICatalogManagement`, `ISearch` and `ISync`. Each passes every call on to the
  service it wraps and, when the call changed something, appends an entry in the
  same unit of work. A call that throws records nothing. The composition root
  hands the primary adapters the decorators instead of the services, so the
  services know nothing about the audit.
- `IUnitOfWork` joins a transaction already running on the same thread, so a
  change and its entry are saved together, or not at all.
- What is recorded: logins (and wrong passwords on an existing account), logouts,
  links created and used, users, roles, permissions and deactivations, files
  added, deleted, edited and tagged, collections created and modified, offline
  downloads, reindexing, backups and changes of library path. Passwords are never
  recorded. Browsing, searching and playing are not: they would bury the
  changes, and what a person watches is their own business.
- The driving port `IAuditTrail` reads the trail with an `AuditQuery` (filters
  by actor, resource, subject, actions and time, newest first, at most 500).
  Administrators read all of it; anyone else only the history of a file or
  collection they can see.
- The REST API exposes it as `GET /api/v1/audit`.
- It is part of version 1.0, added to the scope of ADR 0009.

## Consequences

- The services stay as they are, and the audit can be tested on its own with
  in-memory ports.
- Every change made through the driving ports is recorded, whichever primary
  adapter made it (web UI, API, command line, scheduler).
- A change made without going through a driving port is not recorded; there is
  none in version 1.0.
- An unknown username trying to log in is not recorded, since there is no
  account to attach it to; the server log still shows it (ADR 0021).
- The trail grows without limit. Pruning old entries, if it is ever needed, is a
  later decision.
