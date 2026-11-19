# 0011. Snapshots to expose aggregate state

- **Status:** Accepted
- **Date:** 2026-11-19

## Context

The domain model has no getters or setters: every method expresses a rule. That
keeps the entities from becoming plain data holders, but leaves two needs
unmet:

- the persistence mappers must read an aggregate's state to write it, and
  rebuild an aggregate from the database without running its creation rules or
  recording events again;
- the driving ports return domain objects (`MediaFile`, `Collection`,
  `PlaybackSession`, `SharedLink`), which the REST API must turn into JSON. If
  presentation got the live entities, it could also call their methods without
  going through a service.

## Decision

Each aggregate exposes its state through an immutable **snapshot**, following
the Memento pattern:

- Every aggregate root has `toSnapshot()`, which returns a Java `record` with
  its complete state, and a static `fromSnapshot(snapshot)`, which rebuilds it
  without validating or recording events. Entities inside an aggregate
  (`MediaFile`, `Collection`) have nested snapshot records reached through
  their root.
- Snapshots live in the domain module, next to their aggregate, and carry no
  behavior.
- The persistence mappers translate between snapshots and database rows; they
  never touch the entities directly.
- The driving ports return snapshots instead of entities.

In Memento terms, the aggregate is the originator, the snapshot is the memento,
and the repository is the caretaker. Here the memento is used to persist and
expose state, not to undo changes.

## Consequences

- The entities keep their rules and no getters, and nothing outside the domain
  can change them except through a service.
- Each aggregate gains one or more snapshot records, which must change whenever
  the aggregate's state changes. Tests check that `fromSnapshot(toSnapshot())`
  gives back an equivalent aggregate.
- The REST API serializes snapshots to JSON as they are. If the API ever needs a
  shape different from the domain state, presentation can map snapshots to its
  own response objects without changing the domain.
- The domain, services and ports diagrams must be updated with the snapshots and
  the new return types, and the glossary with the new terms.
