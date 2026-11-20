# 0023. REST API contract

- **Status:** Accepted
- **Date:** 2026-11-20

## Context

The REST API is the main way in for the web UI and for other clients (ADR 0009,
ADR 0010). Its routes, the shape of its JSON, how a request is authenticated and
which HTTP status each error gets have to be fixed before the presentation
module is written, so that the web UI and the API can be built against the same
contract.

Most of the JSON is already defined: the driving ports return snapshots and
DTOs, and the API serializes them as they are (ADR 0011).

## Decision

- The contract is written in Markdown in
  [`docs/reference/rest-api.md`](../reference/rest-api.md): routes, the port
  method behind each one, authentication, JSON conventions and the error table.
  It is not an OpenAPI file: the bodies are the snapshots and DTOs, which the
  domain already defines, and a second copy of their schemas would drift.
- Every route lives under `/api/v1`. A change that breaks clients means a new
  version prefix.
- A client logs in once and sends the session token as
  `Authorization: Bearer <token>`. The web UI also gets it as an `HttpOnly`
  cookie, because the browser cannot add headers to the media it plays with
  `<video>` and `<audio>`.
- Errors are JSON with a fixed shape and a status taken from the exception
  type, as the contract's table says. Unexpected errors answer 500 without
  details; the details go to the log (ADR 0021).
- Anything that changes the contract changes `rest-api.md` in the same commit.

## Consequences

- The presentation module, the web UI and the tests have one place to read the
  API from.
- The document must be kept up to date by hand; reviews check it.
- Clients that need generated code would need an OpenAPI file later.
