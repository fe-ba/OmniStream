# REST API

Contract of the HTTP API that `RestApiAdapter` serves (ADR 0014, ADR 0023). Each
route calls one method of a driving port; the port's Javadoc and the services
explain the rules behind it. Any change to this contract changes this file in the
same commit.

## Basics

- Base path: `/api/v1`. Bodies are JSON (`Content-Type: application/json`),
  except uploads (`multipart/form-data`) and media (bytes).
- **Authentication.** `POST /auth/login` returns a session token. Every other
  route needs it, unless the table says *public*, in one of two ways:
  - `Authorization: Bearer <token>`, for any client;
  - the `omnistream_token` cookie (`HttpOnly`, `SameSite=Strict`), which login
    and shared links also set, for the web UI: the browser cannot add headers
    to what `<video>` and `<audio>` load.

  The adapter validates the token with `IAccessControl.validateToken` and passes
  its `userId` to the port. A missing, unknown or expired token answers 401.
- **Who.** *user* means any logged-in user, guests included; what they may do
  with each item is decided by the domain (`AccessPolicy`). *admin* means the
  service requires an administrator. *manager* means an administrator, or a
  user with `MANAGE` on the resource, directly or through a collection that
  contains it (ADR 0024).

## JSON conventions

The bodies are the snapshots and DTOs of the domain, serialized as they are
(ADR 0011), with these rules:

| Java | JSON | Example |
|---|---|---|
| Identifiers (`ItemId`, `UserId`, `SessionId`, `SharedLinkId`, `CatalogId`) | the UUID as a string | `"3f2c...-..."` |
| `Instant` | ISO-8601 string, UTC | `"2026-11-20T15:04:05Z"` |
| `Duration` | seconds, as a number with decimals, like `HTMLMediaElement.currentTime` | `2712.5` |
| `Optional<T>` | the value, or `null` | |
| Enums | their name | `"READ"` |
| `Set`, `List` | arrays | |
| `Path` | string | |
| Other records (`Tag`, `MetadataEntry`, `Fingerprint`, `Permission`...) | object with the record's components | `{"name": "jazz", "source": "MANUAL"}` |
| `MediaFileSnapshot`, `CollectionSnapshot` | the snapshot, starting with `"kind": "file"` or `"kind": "collection"`, so the items of a mixed list (`CatalogItemSnapshot`) can be told apart | |

In request bodies, a field marked `?` may be left out; leaving out any other
field answers 400. Unknown fields are ignored.

Names are the record components, in camelCase; other methods of a record, such
as `Tag.isAutomatic()`, do not appear. Lists are not paginated in version 1.0.
`ApiJson` implements these rules.

## Errors

Every error answers this body, with the status from the table:

```json
{ "error": "AccessDenied", "message": "Ana may not delete 3f2c..." }
```

| Cause | Status | `error` |
|---|---|---|
| No token, or an invalid or expired one | 401 | `Unauthenticated` |
| `InvalidCredentials` | 401 | `InvalidCredentials` |
| `AccessDenied` | 403 | `AccessDenied` |
| `ResourceNotFound` | 404 | `ResourceNotFound` |
| `VersionConflict` | 409 | `VersionConflict` |
| `CyclicCollection` | 409 | `CyclicCollection` |
| `ExpiredLink` | 410 | `ExpiredLink` |
| `FileRejected` | 422 | `FileRejected` |
| Malformed body or parameter (JSON that cannot be read), `IllegalArgumentException` | 400 | `BadRequest` |
| `IllegalStateException` (the object cannot do that now) | 409 | `Conflict` |
| A route that does not exist | 404 | `NotFound` |
| Any other HTTP error of the server (405...) | its status | the status name without spaces (`MethodNotAllowed`) |
| Anything else | 500 | `InternalError`, with no details; they go to the log |

`ApiErrors` implements this table. A new domain exception needs a row here, and
in `ApiErrors`, before it reaches the API; until then it answers 500.

## Web UI

`WebUiAdapter` serves the browser interface from the same server: `/` and the
files next to it (`/app.js`, `/style.css`, `/fonts/`), and `/s/{token}` for
shared links. The page uses this API like any other client. Every path under
`/api/v1` that is not a route below is a JSON 404, never the page.

What the page offers, and the routes behind it:

- **Library:** browse, search, upload, collections, tags, play (`/library`,
  `/collections`, `/files`, `/sessions`).
- **Sharing and access:** a link per collection (`/collections/{id}/links`);
  "Who has access" for whoever manages a collection (`/items/{id}/access`,
  `/users/by-name/{username}`, `/users/{id}/permissions`).
- **People** (administrators): users, roles, access and deactivation (`/users`).
- **History:** the whole trail for administrators, and the history of each file
  or collection for whoever can see it (`/audit`).
- **Live:** what others change appears without reloading (`/events`).

## Routes

### Access (`IAccessControl`)

| Method and path | Port method | Who | Request | Response |
|---|---|---|---|---|
| `POST /auth/login` | `authenticateUser` | public | `{"provider"?, "username", "password"}`; `provider` is `"local"` when left out | 200 `SessionToken` and the cookie |
| `GET /auth/session` | `validateToken` | user | | 200 `{"userId", "role", "expiresAt"}`: who is logged in, without the token, so the web UI can tell after a reload |
| `POST /auth/logout` | `logout` | user | | 204, and the cookie is cleared |
| `POST /links/{token}/access` | `accessWithLink` | public | | 200 `SessionToken` of the link's guest, and the cookie |
| `POST /collections/{id}/links` | `shareCollection` | user | `{"level": "READ", "lifetime": 604800}` | 201 `SharedLinkSnapshot` |
| `POST /users` | `createUser` | admin | `{"username", "password", "role"}` | 201 `{"id"}` |
| `PUT /users/{id}/role` | `manageRoles` | admin | `{"role": "ADMIN"}` | 204 |
| `POST /users/{id}/permissions` | `grantPermission` | manager | `{"resource", "level"}` | 204 |
| `DELETE /users/{id}/permissions/{resource}?level=READ` | `revokePermission` | manager | | 204 |
| `POST /users/{id}/deactivate` | `deactivateUser` | admin | | 204 |
| `GET /users` | `listUsers` | admin | | 200 list of `UserSnapshot` |
| `GET /users/by-name/{username}` | `findUser` | admin, or anyone with `MANAGE` on something | | 200 `{"id"}` |
| `GET /items/{id}/access` | `accessTo` | manager of the item | | 200 list of `AccessGrant` |

`SessionToken` is `{"value", "userId", "role", "expiresAt"}`. `UserSnapshot` is
`{"id", "username", "role", "permissions": [{"resource", "level"}],
"identities": [{"issuer", "subject"}], "active"}`; guests are not listed.
`AccessGrant` is `{"user", "username", "level"}`: who has a permission on the
item itself, not through a collection that contains it.

A manager who is not an administrator may only give or take `READ` and `WRITE`,
not change its own permissions, and not touch another manager of the same
resource; otherwise 403 `AccessDenied`. The web UI opens a shared link as
`/s/{token}?collection={id}`, a page that calls `POST /links/{token}/access` and
then shows that collection.

### Catalog (`ICatalogManagement`)

| Method and path | Port method | Who | Request | Response |
|---|---|---|---|---|
| `POST /files` | `uploadFile` | user | multipart, field `file` | 201 `IngestionResult` if indexed, 200 if not (a rejected duplicate) |
| `GET /files/duplicates` | `detectDuplicates` | user | | 200 `[MediaFileSnapshot]` |
| `PUT /files/{id}/metadata/{key}` | `editMetadata` | user | `{"value"}` | 204 |
| `DELETE /files/{id}` | `deleteFile` | user | | 204 |
| `POST /admin/reindex` | `reindexLibrary` | admin | `{"root"}` (server path) | 200 `{"indexed": 42}` |
| `POST /admin/backup` | `backupCatalog` | admin | `{"destination"}` (server path) | 200 `{"path"}` |
| `PUT /admin/library-path` | `configureLibraryPath` | admin | `{"path"}` | 204 |
| `GET /admin/status` | `monitorServerStatus` | admin | | 200 `ServerStatus` |

`ingestFile` takes a path on the server's disk, so it has no route: only the CLI
and the desktop app, which run on that machine, call it.

### Search (`ISearch`)

| Method and path | Port method | Who | Request | Response |
|---|---|---|---|---|
| `GET /search?q=text` | `searchContent` | user | `q` required | 200 `[CatalogItemSnapshot]` |
| `GET /files?text=&type=VIDEO&tag=jazz&tag=live` | `filterByCriteria` | user | every parameter optional | 200 `[MediaFileSnapshot]` |
| `GET /library` | `browseLibrary(u)` | user | | 200 `[CatalogItemSnapshot]`, the top level: what is not inside any collection |
| `GET /collections/{id}/items` | `browseLibrary(u, col)` | user | | 200 `[CatalogItemSnapshot]` |
| `POST /collections` | `manageCollections` | user | `{"name", "itemsToAdd"?: [ids]}` | 201 `CollectionSnapshot` |
| `PATCH /collections/{id}` | `manageCollections` | user | `{"name"?, "itemsToAdd"?}` | 200 `CollectionSnapshot` |
| `POST /files/{id}/tags` | `tagFile` | user | `{"name": "jazz"}`; the tag is `MANUAL` | 204 |

### Playback (`IPlayback`)

| Method and path | Port method | Who | Request | Response |
|---|---|---|---|---|
| `POST /files/{id}/sessions` | `openSession` | user | | 201 `PlaybackSessionSnapshot` |
| `POST /files/{id}/sessions/resume` | `resumeFromLastPosition` | user | | 201 `PlaybackSessionSnapshot` |
| `GET /sessions/{id}/stream?codec=&bitrateKbps=&resolution=` | `requestStream` | user | parameters optional; none means "as it is" (`StreamProfile.isDirectPlay`) | 200, or 206 for a `Range`; the media bytes with their MIME type |
| `PUT /sessions/{id}/position` | `recordProgress` | user | `{"position": 754.2}`, not negative | 204 |
| `POST /sessions/{id}/pause` | `pauseSession` | user | | 204 |
| `POST /sessions/{id}/resume` | `resumeSession` | user | | 204 |
| `POST /sessions/{id}/finish` | `finishSession` | user | | 204 |

Only the user who opened a session can use it: for any other user, the routes
under `/sessions/{id}` answer 403. `resumeSession` goes on with a paused session;
`POST /files/{id}/sessions/resume` instead starts a new one where the last
session on that file stopped.

A file sent as it is has a known size (`MediaStream.sizeBytes`), so the stream
answers HTTP range requests (`Accept-Ranges: bytes`) and the player can jump to
any position. A transcoded stream has no size: it is sent as it is produced,
without ranges, starting at the session's position.

### Synchronization (`ISync`)

| Method and path | Port method | Who | Request | Response |
|---|---|---|---|---|
| `GET /sync/changes?since=<instant>` | `syncChanges` | user | `since` required, ISO-8601 | 200 `SyncDelta` |
| `GET /sync/pending?since=<instant>` | `hasPendingChanges` | user | `since` required, ISO-8601 | 200 `{"pending": true}` |
| `GET /files/{id}/offline` | `cacheContent` | user | | 200, the file as an attachment; its metadata is in the file's snapshot |

`IEventDispatch` has no route: the scheduler calls it.

### Audit trail (`IAuditTrail`, ADR 0025)

| Method and path | Port method | Who | Request | Response |
|---|---|---|---|---|
| `GET /audit` | `history` | admin: any query; anyone else: only with `resource`, on something they can see | query string below | 200 list of `AuditEntry`, newest first |

Every filter is optional: `actor`, `resource` and `subject` (identifiers),
`action` (an `AuditAction`, repeatable: `?action=FILE_ADDED&action=FILE_DELETED`),
`from` (included) and `to` (excluded) as ISO-8601 instants, and `limit` (1 to
500, 100 when left out). `?resource=<id>` alone is the history of a file or a
collection.

`AuditEntry` is `{"id", "at", "actor", "action", "resource", "subject",
"detail"}`; `resource` and `subject` are `null` when they do not apply, and
`detail` is `""` when there is nothing to add. The trail only records what
changes something or matters for security: logins, links, users and
permissions, files and collections, and the administration of the library.
Browsing, searching and playing are not recorded.

### Live changes (`ICatalogFeed`, ADR 0026)

| Method and path | Port method | Who | Request | Response |
|---|---|---|---|---|
| `GET /events` | `follow` | user | header `Accept: text/event-stream` (400 without it) | 200, a stream of server-sent events that stays open |

The stream sends `ready` (data `{}`) when it opens, then one `change` for each
change to a file or collection the user can see:

```
event: change
data: {"item": "3f2c...", "change": "MODIFIED", "event": "FileModified", "at": "2026-11-21T17:00:00Z"}
```

`change` is `ADDED`, `MODIFIED` or `REMOVED`; `event` is the domain event behind
it. Removals reach everyone, with only the identifier. In the browser,
`new EventSource("/api/v1/events")` sends the session cookie; when the
connection drops it reconnects by itself and gets a new `ready`, which is the
moment for the client to reload what it shows. Changes reach the stream when
the outbox is published (`SyncTask`), within a couple of seconds.

### Editing text together (`ITextEditing`, ADR 0027)

| Method and path | Port method | Who | Request | Response |
|---|---|---|---|---|
| `GET /files/{id}/edit` (WebSocket) | `open`, `save` | read to watch, write to edit | session cookie or `Authorization` header; a browser's `Origin` must be this server | 101, a WebSocket; 401 without a session; 403 from another site |

Everyone editing the same file is in one room. The server relays the edits
without decoding them; the browsers merge them with Yjs. Only text files can be
edited: plain text, Markdown, subtitles, configuration and source code, of at
most 1 MiB, in UTF-8.

If the file cannot be opened, the connection closes with 4000 plus the status
the API would answer, and the message as the reason: 4400 (not an identifier),
4403, 4404 or 4422 (not a text file).

Edits are **binary frames**; the first byte says what follows:

| Byte | From | Content |
|---|---|---|
| `0` | browser, relayed to the others | a Yjs update. Kept, so whoever joins later receives it. Dropped if the sender may only read. |
| `1` | browser, relayed to the others | Yjs awareness: who is there and their cursor. Not kept. |
| `2` | browser | the whole document as one update, in answer to `compact`. It replaces the updates kept so far. |
| `3` | browser | the text to save, in UTF-8. |

The server talks in **JSON text messages**, each with a `type`:

| `type` | When | Other fields |
|---|---|---|
| `welcome` | on joining | `file`, `name`, `text`, `fingerprint`, `editable`, `seed`, `others` (how many were already there); then the kept updates follow as frames |
| `seed` | the one who had to load the text left before doing it | none: load `text` from `welcome` now |
| `saved` | someone saved, to everyone | `fingerprint`, `by` (user identifier) |
| `compact` | many updates are kept | none: answer with frame `2` |
| `error` | something the sender did failed | `error`, `message`, as in error responses |

The first editor to join gets `seed: true` and loads `text` into the empty
document; the others receive its update. Readers never seed: until an editor
does, they show `text`. A save is based on the last fingerprint the room knows,
so editors do not send one; saving the text already stored changes nothing,
and a save after the file was changed outside the editor is an `error` with
`VersionConflict`. A save before anything was loaded is refused (`NotReady`),
so an empty document never overwrites the file.
