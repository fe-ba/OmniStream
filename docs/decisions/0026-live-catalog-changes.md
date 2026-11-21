# 0026. Live catalog changes over server-sent events

- **Status:** Accepted
- **Date:** 2026-11-21

## Context

When several people use the same server, what one of them does should show up
for the others without reloading: a file uploaded to a shared collection, a
renamed collection, a deleted file. Today the web UI only sees changes when it
asks again.

The domain already announces every change: `CatalogIndex` records catalog
events, the persistence adapter writes them to the outbox, `SyncTask` publishes
them on the event bus, and observers receive them (ADR 0011). What is missing is
a way to pass them to the clients that are open, without telling anyone about
files they cannot see.

## Decision

We will push catalog changes to open clients with server-sent events:

- A new driving port, `ICatalogFeed.follow(u, listener)`, returns a
  `FeedSubscription`. `CatalogFeedService` implements it.
- Each follower is an observer of its own on the event bus (Observer): the bus
  stays the only owner of the observer list, as the design requires, and closing
  the subscription unsubscribes it.
- A follower only hears about what its user can see, counting the collections
  that contain each item (`AccessPolicy` with a `CatalogSnapshot`). The user and
  the catalog are read again for each change, so a permission taken away takes
  effect at once. A removal is passed on to everyone, with only the identifier:
  the catalog no longer says who could see the item. A deactivated follower, or
  one whose listener fails, stops following.
- The REST API exposes it as `GET /api/v1/events`, a stream of server-sent
  events (`ready`, then `change`). It follows before the response starts, so an
  error still answers with its status code.
- Server-sent events and not WebSockets: the changes only go from the server to
  the browser, the browser's `EventSource` sends the session cookie and
  reconnects by itself, and it is plain HTTP. When the client reconnects it gets
  a new `ready` and reloads what it shows, so it misses nothing in between.
  Editing text together (version 1.0, later) needs both directions and will use
  WebSockets; that is a separate decision.
- Changes keep going through the outbox, so a change that was not committed is
  never announced. The composition root runs `SyncTask` every two seconds, which
  is how late a change can arrive.

## Consequences

- People sharing a server see each other's changes within a couple of seconds,
  in the web UI and in any client that reads the stream.
- The services that change the catalog are untouched: the feed only listens.
- Every open stream holds a connection, and each change reads the user and the
  catalog once per follower. For the tens of users of a home or small office
  server that is cheap; the cached catalog repository helps.
- With more than one server instance, each would only hear its own events; a
  shared event bus would be needed then. Version 1.0 runs one instance.
