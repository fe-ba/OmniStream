# 0019. Package structure

- **Status:** Accepted
- **Date:** 2026-11-19

## Context

The domain alone has more than a hundred types, and three people will write
them at the same time. Without an agreed structure, each person would organize
their part differently and the packages would not show the architecture.

The code is written in English (ADR 0006), with the names in the glossary
(`docs/reference/glossary.md`). The modules follow the layers (ADR 0013), and
the team splits the domain work into the model, the ports and adapters, and
the services.

## Decision

Packages are organized by layer first and then by area. The root package of
each module is `co.omnistream.<module>`.

**`omnistream-domain`**

| Package | Contents |
|---|---|
| `model.catalog` | `CatalogIndex`, `CatalogItem`, `MediaFile`, `Collection`, `MetadataEntry`, `Tag`, `Fingerprint`, `IngestionResult`, `Classification`, `CatalogId`, `ItemId`, and their enums |
| `model.access` | `User`, `Permission`, `LinkedIdentity`, `UserId`, `Role`, `PermissionLevel`, `AccessType` |
| `model.sharing` | `SharedLink`, `SharedLinkId` |
| `model.playback` | `PlaybackSession`, `SessionId`, `SessionStatus` |
| `event` | `DomainEvent`, `CatalogEvent` and its subclasses, `EventId`, `ChangeType`, `ICatalogObserver` |
| `snapshot` | The `...Snapshot` records |
| `exception` | `DomainException` and its subclasses |
| `policy` | `AccessPolicy`, `DeduplicationPolicy` |
| `port.in` | Driving ports |
| `port.out` | Driven ports |
| `port.dto` | DTOs and commands used by the ports (`SessionToken`, `SearchCriteria`, `StreamProfile`, `MediaStream`, ...) |
| `service` | `AuthorizedService`, the domain services and `SyncObserver` |
| `service.ingestion` | `IngestionStep`, its steps and `IngestionContext` |
| `service.classification` | `ClassificationRule` and its implementations |
| `service.stream` | `StreamStrategy`, `DirectStream`, `TranscodedStream` |

**`omnistream-infrastructure`**

| Package | Contents |
|---|---|
| `persistence` | `JdbcPersistenceAdapter`, its SQLite and PostgreSQL subclasses, `CachedCatalogRepository` |
| `persistence.mapper` | Row mappers |
| `storage` | File storage adapters |
| `media` | `MetadataAdapter`, `TranscodingAdapter`, `CachedTranscoder` |
| `identity` | Identity adapters |
| `token` | `InMemoryTokenAdapter` |
| `event` | `EventBusAdapter` |

**`omnistream-presentation`**: `rest`, `web`, `cli`, `scheduler` (with the
scheduled tasks).

**`omnistream-bootstrap`**: the root package holds `CompositionRoot` and the
`main` class; `factory` holds the infrastructure factories; `config` holds
`AppConfiguration`.

Each package in the domain has one owner, following the team split: the model
owns `model.*`, `event`, `snapshot`, `exception` and `policy`; ports and
adapters own `port.*` and the infrastructure module; services own `service.*`.
Changes to another owner's package go through a pull request reviewed by that
owner.

## Consequences

- The package tree shows the hexagonal architecture: anyone can tell a port
  from an adapter or a service by its package.
- Each person works mostly in their own packages, which reduces merge
  conflicts.
- Related types of one feature are spread across packages (for example, the
  catalog model, its port and its service). Finding them relies on the
  glossary and the diagrams.
- Moving a type to another package later means changing its imports in every
  module.
