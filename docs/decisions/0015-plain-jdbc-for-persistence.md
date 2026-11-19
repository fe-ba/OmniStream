# 0015. Plain JDBC for persistence

- **Status:** Accepted
- **Date:** 2026-11-19

## Context

Version 1.0 stores its data in SQLite or PostgreSQL, depending on the profile
(ADR 0009). The design already describes the persistence adapter:

- an abstract `JdbcPersistenceAdapter` implements the seven persistence ports
  once;
- `SqlitePersistenceAdapter` and `PostgreSqlPersistenceAdapter` change only how
  the connection is obtained (Template Method);
- mappers translate between aggregates and rows.

An ORM such as JPA/Hibernate needs annotations, a no-argument constructor and
accessors on the entities. That conflicts with a domain that depends on nothing
and exposes no getters (ADRs 0011 and 0013).

## Decision

The persistence adapter uses the **JDBC API directly**, as the design describes:

- `JdbcPersistenceAdapter` holds the shared SQL and the mappers; each subclass
  provides `connection()`. SQLite uses the Xerial SQLite JDBC driver and
  PostgreSQL the official PostgreSQL JDBC driver.
- Mappers translate between snapshots (ADR 0011) and rows, so they never read
  entities directly.
- The SQL is kept to the subset that both databases understand. Where they
  differ, the difference goes into an overridable method of the subclass.
- Transactions are handled through the connection (auto-commit off). This is
  what `IUnitOfWork` and the transactional outbox rely on.
- How the schema is created and migrated is decided together with the database
  schema.

## Consequences

- The SQL is visible and under the team's control, and the Template Method of
  the design shows up as written in the code.
- No ORM annotations or rules leak into the domain.
- There is more repetitive code (parameter binding, reading result sets) than
  with an ORM or a helper library. That code stays in the mappers and the
  adapter.
- Every query has to work on both SQLite and PostgreSQL. Tests for the adapter
  run against both.
