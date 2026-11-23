# 0028. One installation per context

- **Status:** Accepted
- **Date:** 2026-11-22

## Context

OmniStream serves two kinds of users. A person installs it on their own
computer to keep and play their media (the local profile, ADR 0010). A company
or a group installs it on a server, with an administrator, roles and
permissions per collection (the server profile, ADR 0024).

The question is whether one installation should also hold both at once: a
person's own library and the company's, kept apart, with a way to switch
between them. That is multi-tenancy: every catalog, user, permission, audit
entry and setting would belong to a tenant, and every query and every check
would have to carry it.

## Decision

We will not support several tenants in one installation. Each context has its
own installation, with its own catalog, users, audit trail and configuration:

- A person who also works with a company's OmniStream runs their own
  installation for their personal files and uses the company's through its
  address, with the account the company gave them.
- Inside one installation, separation comes from roles and permissions:
  collections that someone cannot read are not in their catalog, their search
  or their live changes (ADR 0024, ADR 0026).
- The profile chooses the infrastructure, not the kind of user: a small group
  can use the local profile, and one person can use the server profile.

## Consequences

- Personal files never end up in the company's database, backups or audit
  trail, and the company's administrator has no say over them. The boundary is
  one that anyone understands: a different address.
- The domain and the adapters stay as they are; no tenant identifier crosses
  every port, table and query.
- Someone with both needs runs two installations and logs in to each one
  separately. There is no single screen that shows both libraries.
- Offering OmniStream as a hosted service for many organizations would need
  multi-tenancy. That is outside the scope of version 1.0 (ADR 0009); it would
  be a new decision.
