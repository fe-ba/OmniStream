# 0024. Delegate permissions per collection

- **Status:** Accepted
- **Date:** 2026-11-21

## Context

In a company, one administrator cannot decide every permission on every
collection. The people in charge of an area know who should see or edit its
files, and should be able to share them without asking the administrator each
time.

The access model already has what is needed to describe that: permissions per
resource with three levels (`READ` < `WRITE` < `MANAGE`), and a permission on a
collection that reaches everything inside it. Until now only an administrator
could grant or revoke permissions.

We considered a formal hierarchy instead: a manager role with employees
assigned to it, and teams. It needs a new aggregate, new ports, a new screen and
rules about who belongs to which team. The course project does not need that
yet.

## Decision

We will let whoever manages a resource hand out access to it:

- An administrator may grant or revoke any permission, as before.
- A user with `MANAGE` on a resource, directly or through a collection that
  contains it, may grant or revoke `READ` and `WRITE` on it.
- `MANAGE` itself is only given or taken by an administrator: who is in charge
  of an area is an administrator's decision.
- A manager may not change its own permissions, nor those of someone who also
  manages the resource (administrators included), so that two managers cannot
  take a collection from each other.
- Guests never delegate, whatever the level of their link.
- Who may grant a permission may also take it back: one rule,
  `AccessPolicy.mayDelegate`, covers both.

The rule is a domain policy and has unit tests. Resolving the level a granter
reaches through collections needs the catalog, so `AccessService` resolves it
and passes it to the policy.

To manage access, the driving port `IAccessControl` also gets:

- `listUsers(admin)`, for the administration screen;
- `findUser(u, username)`, so a manager can find the person to share with
  without listing every user;
- `accessTo(u, resource)`, the list of who has a permission on a resource,
  visible to its managers.

`IUserRepository` gets `findAll()`; the services filter it, since a server has
tens of users, not thousands.

## Consequences

- An area can be run by its own manager; the administrator only appoints
  managers and handles users.
- No new roles, aggregates or tables: the rule sits on the permissions that
  already exist.
- A manager sees the usernames of the people it looks up, and the access list
  of what it manages. That is acceptable inside one organization.
- Teams with assigned employees remain possible later, as a new decision that
  builds on this one.
