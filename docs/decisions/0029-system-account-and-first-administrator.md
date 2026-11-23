# 0029. System account and first administrator

- **Status:** Accepted
- **Date:** 2026-11-22

## Context

Every driving port takes the user who acts, and every service checks what
that user may do. Two callers have no person behind them: the command line,
run by whoever has the machine, and the scheduled tasks (ADR 0023). They need
a user too.

A new installation has no users at all, and only an administrator can create
them. Someone has to be able to log in the first time without editing the
database by hand.

## Decision

The composition root makes sure that two accounts exist, every time the
application starts, before it serves anything:

- **The system account**, `omnistream-system`, an administrator whose only
  identity is `("system", "omnistream")`. No identity provider recognizes the
  `system` issuer and the account has no password, so nobody can log in with
  it. The command line and the scheduler act as this account. The
  administration screen does not list it.
- **The first administrator**, `admin`, created only when no active
  administrator can log in (no active administrator other than the system
  account). Its password is random: 20 letters and digits in groups of five,
  leaving out the ones that look alike (`0`/`O`, `1`/`l`/`I`). It is printed
  once on the console of whoever starts the server, and never written to the
  log file or anywhere else. If the username `admin` already belongs to someone
  who is not an administrator, nobody is created and a warning is logged.

## Consequences

- A new installation works on the first start: whoever installed it reads the
  password on the screen, logs in and creates the other accounts.
- The audit trail shows `omnistream-system` for what the command line and the
  scheduled tasks did, instead of an empty actor.
- The password cannot be read again later. Losing it before creating another
  administrator means starting over with an empty user table, until there is a
  way to change or reset a password (`ICredentialRegistry.changePassword`
  exists, but no route or command uses it yet).
- A server started as a background service shows the password in its service
  log (for example `journalctl`). That is the console of that installation;
  whoever reads it can already read the database.
- Deactivating every administrator makes the next start create `admin` again,
  which is a way back in for whoever controls the machine, and only for them.
