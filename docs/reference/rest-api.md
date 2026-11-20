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
  service requires an administrator.

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
| `CatalogItemSnapshot` | the file or collection snapshot plus `"kind": "file"` or `"kind": "collection"` | |

Names are the record components, in camelCase. Lists are not paginated in
version 1.0.

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
| Malformed body or parameter, `IllegalArgumentException` | 400 | `BadRequest` |
| `IllegalStateException` (the object cannot do that now) | 409 | `Conflict` |
| Anything else | 500 | `InternalError`, with no details; they go to the log |

A new domain exception needs a row here before it reaches the API.

## Routes

### Access (`IAccessControl`)

| Method and path | Port method | Who | Request | Response |
|---|---|---|---|---|
| `POST /auth/login` | `authenticateUser` | public | `{"provider": "local", "username", "password"}` | 200 `SessionToken` and the cookie |
| `POST /auth/logout` | `logout` | user | | 204, and the cookie is cleared |
| `POST /links/{token}/access` | `accessWithLink` | public | | 200 `SessionToken` of the link's guest, and the cookie |
| `POST /collections/{id}/links` | `shareCollection` | user | `{"level": "READ", "lifetime": 604800}` | 201 `SharedLinkSnapshot` |
| `POST /users` | `createUser` | admin | `{"username", "password", "role"}` | 201 `{"id"}` |
| `PUT /users/{id}/role` | `manageRoles` | admin | `{"role": "ADMIN"}` | 204 |
| `POST /users/{id}/permissions` | `grantPermission` | admin | `{"resource", "level"}` | 204 |
| `DELETE /users/{id}/permissions/{resource}?level=READ` | `revokePermission` | admin | | 204 |
| `POST /users/{id}/deactivate` | `deactivateUser` | admin | | 204 |

`SessionToken` is `{"value", "userId", "role", "expiresAt"}`. The web UI opens a
shared link as `/s/{token}`, a page that calls `POST /links/{token}/access`.

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
| `GET /search?q=text` | `searchContent` | user | | 200 `[CatalogItemSnapshot]` |
| `GET /files?text=&type=VIDEO&tag=jazz&tag=live` | `filterByCriteria` | user | every parameter optional | 200 `[MediaFileSnapshot]` |
| `GET /collections/{id}/items` | `browseLibrary` | user | | 200 `[CatalogItemSnapshot]` |
| `POST /collections` | `manageCollections` | user | `{"name", "itemsToAdd": [ids]}` | 201 `CollectionSnapshot` |
| `PATCH /collections/{id}` | `manageCollections` | user | `{"name"?, "itemsToAdd"?}` | 200 `CollectionSnapshot` |
| `POST /files/{id}/tags` | `tagFile` | user | `{"name": "jazz"}` | 204 |

### Playback (`IPlayback`)

| Method and path | Port method | Who | Request | Response |
|---|---|---|---|---|
| `POST /files/{id}/sessions` | `openSession` | user | | 201 `PlaybackSessionSnapshot` |
| `POST /files/{id}/sessions/resume` | `resumeFromLastPosition` | user | | 201 `PlaybackSessionSnapshot` |
| `GET /sessions/{id}/stream?codec=&bitrateKbps=&resolution=` | `requestStream` | user | parameters optional; `Range` header supported | 200 or 206, the media bytes with their MIME type |
| `PUT /sessions/{id}/position` | `recordProgress` | user | `{"position": 754.2}` | 204 |
| `POST /sessions/{id}/pause` | `pauseSession` | user | | 204 |
| `POST /sessions/{id}/finish` | `finishSession` | user | | 204 |

### Synchronization (`ISync`)

| Method and path | Port method | Who | Request | Response |
|---|---|---|---|---|
| `GET /sync/changes?since=<instant>` | `syncChanges` | user | | 200 `SyncDelta` |
| `GET /sync/pending?since=<instant>` | `hasPendingChanges` | user | | 200 `{"pending": true}` |
| `GET /files/{id}/offline` | `cacheContent` | user | | 200, the file as an attachment; its metadata is in the file's snapshot |

`IEventDispatch` has no route: the scheduler calls it.

## Open points

These come from the current driving ports and need a decision in the ports
review (issue #5) before the routes involved are built:

1. **Sessions do not know who is asking.** `requestStream`, `recordProgress`,
   `pauseSession` and `finishSession` take only a `SessionId`, so the service
   cannot check that the session belongs to the user of the token. Proposal:
   add `UserId u` as their first parameter.
2. **A paused session cannot go on.** `PlaybackSession.resume()` exists, but
   `IPlayback` has no method for it. Proposal: `resumeSession(UserId u,
   SessionId s)`, route `POST /sessions/{id}/resume`.
3. **The top level of the library cannot be browsed.** `browseLibrary` needs a
   collection. Proposal: `browseLibrary(UserId u)`, which returns the files and
   collections that are not inside another collection, route `GET /library`.
