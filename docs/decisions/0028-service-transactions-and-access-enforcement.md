# 0028. Service transactions and access enforcement

- **Status:** Proposed
- **Date:** 2026-11-21

## Context

In the domain services, indexing a new file (`CatalogService.ingestFile` and
`uploadFile`) or creating a collection (`SearchService.manageCollections`)
modifies `CatalogIndex` and must grant `PermissionLevel.MANAGE` on `User` so the
creator can reach what they added (access is denied by default). Persisting the
two aggregates separately risks leaving an item unreachable if the second write
fails. `IUnitOfWork` already joins an active transaction on the same thread (ADR
0025), so services can wrap multi-aggregate operations safely under the audit
decorators.

`AccessPolicy` already resolves inherited permissions through nested
collections via `authorizes(User, ItemId, AccessType, CatalogSnapshot)`, yet
`AuthorizedService` only exposes `checkAccess(UserId, ItemId, AccessType)`. A
shared helper overload avoids repeating the load-and-throw boilerplate across
`CatalogService`, `SearchService`, `PlaybackService` and `SyncService` without
coupling `AccessService` to `ICatalogRepository` inside `AuthorizedService`.

Finally, `User` throws `IllegalArgumentException` or `IllegalStateException` on
guest invariants (`Role.GUEST` or guest targets), while `rest-api.md` expects
business errors (`AccessDenied`, 403) or conflict errors (`IllegalStateException`,
409) depending on the case, and `SyncService` must align removed-item
visibility with `CatalogFeedService` (ADR 0026).

## Decision

We will enforce transactional boundaries and uniform access rules across the
domain services as follows:

- `CatalogService` and `SearchService` receive `IUnitOfWork` in their
  constructors and wrap multi-aggregate writes (`CatalogIndex` save plus
  granting `PermissionLevel.MANAGE` on `User`) inside `unitOfWork.execute(...)`.
- `AuthorizedService` adds `protected void checkAccess(UserId u, ItemId r,
  AccessType acc, CatalogSnapshot catalog)`, which loads the user and throws
  `AccessDenied` unless `accessPolicy.authorizes(user, r, acc, catalog)` holds.
- In `AccessService`, a deactivated user on `authenticateUser` and any forbidden
  operation involving `Role.GUEST` (`createUser` with `Role.GUEST`, or
  `manageRoles`, `grantPermission` and `revokePermission` on a guest target or
  with `Role.GUEST`) throw `AccessDenied`. Creating a user with an already
  registered username throws `IllegalStateException` (HTTP 409 Conflict).
- In `SyncService.filterVisible`, `added` and `modified` identifiers are
  filtered with `accessPolicy.authorizes(user, id, AccessType.VIEW, catalog)`,
  while `removed` identifiers are passed through to active authenticated users,
  matching ADR 0026 because the deleted item is no longer in the catalog tree.

## Consequences

- File ingestion and collection creation become atomic across `CatalogIndex` and
  `User`, and compose cleanly with the audit decorators (ADR 0025).
- Catalog, search, playback and sync services share one authorization helper
  without introducing new `DomainException` subclasses or changing `rest-api.md`.
- `CompositionRoot` must pass `IUnitOfWork` to `CatalogService` and
  `SearchService`.
- The software diagrams (`OmniStream-Services`, `OmniStream-PortsAdapters`,
  `OmniStream-Components` and their Spanish counterparts in `es/`) must be
  updated to show `IUnitOfWork` on `CatalogService` and `SearchService`, update
  the `IUnitOfWork` note, and add the `checkAccess` overload on
  `AuthorizedService`.
