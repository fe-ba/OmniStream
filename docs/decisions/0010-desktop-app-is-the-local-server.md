# 0010. Desktop app is the server in local mode

- **Status:** Accepted
- **Date:** 2026-11-19

## Context

The design includes a "desktop app" as one of OmniStream's clients, with a
`DesktopAdapter` that talks to the domain over IPC. However, nothing in the
design models a desktop client of its own: no local cache, no client side of
the synchronization. It was unclear whether the desktop app was a separate
application or the server itself installed on a personal computer.

OmniStream is self-hosted and mostly offline. The local profile (SQLite and
local disk, created by the local infrastructure factory) is already a
single-machine installation.

## Decision

The desktop app is the OmniStream server running on the user's computer with
the local profile, used through the web UI in the browser at `localhost`.
There is no separate desktop client.

- `DesktopAdapter` is removed from the design.
- The synchronization services (`ISync`, the change log and offline packages)
  stay in the domain and are exposed through the REST API. Their main client
  will be the future mobile app.

## Consequences

- One codebase and one user interface serve both the personal installation and
  the server installation; only the profile changes.
- No IPC protocol or desktop UI toolkit has to be designed or learned.
- The design loses a client from its diagrams: the component, ports and
  adapters and use case diagrams must be updated (both language versions, see
  ADR 0008).
- A native desktop experience (tray icon, file associations) is not available.
  If it is ever needed, a thin launcher can start the local server and open the
  browser without touching the architecture.
