# 0014. Javalin for HTTP

- **Status:** Accepted
- **Date:** 2026-11-19

## Context

The REST API and the web UI need an HTTP server that can:

- route requests;
- read and write JSON;
- accept file uploads (`uploadFile`);
- stream audio and video with HTTP range requests, so playback can jump to any
  position.

The design created its own `HttpRequest` and `HttpResponse` types, but
transported the body as a `String`, which cannot carry uploads or media streams.

The design also builds the application by hand: a composition root and an
Abstract Factory create every object. A framework that does its own dependency
injection would duplicate that work and hide it.

## Decision

The primary HTTP adapters use **Javalin 7**, a small web framework on top of
Jetty 12:

- `RestApiAdapter` registers the API routes and calls the driving ports.
  Errors from the domain are translated into HTTP responses with Javalin's
  exception handlers (`mapError`).
- Media is streamed with `writeSeekableStream`, which handles range requests.
- Uploads are read from Javalin's uploaded files and passed to the domain as an
  `InputStream`.
- JSON uses Jackson, which Javalin supports out of the box. It serializes the
  snapshots returned by the ports (ADR 0011).
- `WebUiAdapter` serves the web UI as static files.
- The composition root creates and starts the Javalin app. No dependency
  injection framework is used.

The custom `HttpRequest` and `HttpResponse` types are removed from the design:
Javalin's `Context` takes their place, and only inside the presentation module.

## Consequences

- Routing, JSON, uploads and range requests come ready-made, so the adapter code
  stays small.
- Javalin and Jetty are dependencies of the presentation module only. The
  domain and infrastructure never see them (ADR 0013).
- The composition root and the Abstract Factory stay explicit and visible in
  the code, as the course requires.
- The ports and adapters diagrams must be updated: the HTTP types go away and
  the HTTP adapters depend on Javalin.
