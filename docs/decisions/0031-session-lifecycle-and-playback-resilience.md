# 0031. Session lifecycle and playback resilience

- **Status:** Proposed
- **Date:** 2026-11-21

## Context

`AppConfiguration` defines `token.lifetime` with default `PT12H` (ADR 0022), yet
`AccessService` had no `Duration` parameter in its constructor and therefore
could not honor the configured session lifetime. In addition, deactivating a
user must revoke access immediately even if the user still holds an unexpired
session token.

On the playback side, `IPlayback` receives `UserId` on every operation so that
only the user who opened a session can control it, and `PlaybackSession` throws
`IllegalStateException` if `advance`, `pause` or `resume` is called after
`SessionStatus.FINISHED`. Media players frequently deliver late progress updates
or pause requests after playback ends, while `GET /sessions/{id}/stream` (HTTP
range requests) runs alongside `POST /sessions/{id}/resume` (`rest-api.md`).

## Decision

We will manage session tokens and playback state transitions as follows:

- `AccessService` receives a positive `Duration tokenLifetime` in its
  constructor and issues opaque tokens of 32 `SecureRandom` bytes encoded in
  URL-safe Base64 without padding, expiring at `clock.instant().plus(tokenLifetime)`.
- `AccessService.validateToken(String)` verifies both
  `token.isValidAt(clock.instant())` and that the token's user still exists and
  is active in `IUserRepository`.
- Every `PlaybackService` method that takes a `SessionId` loads the session
  (`ResourceNotFound` if missing) and throws `AccessDenied` if `session.userId`
  does not equal `u`, administrators included.
- `PlaybackService.recordProgress` and `pauseSession` act as idempotent no-ops
  when the session is already `FINISHED`.
- `PlaybackService.requestStream` revalidates `AccessType.VIEW` on the file,
  rejects `FINISHED` sessions with `AccessDenied`, and leaves `PAUSED` sessions
  unchanged so `GET /sessions/{id}/stream` has no state side effects; resuming a
  `FINISHED` session via `resumeSession` lets `IllegalStateException` propagate
  (HTTP 409 Conflict).
- `DirectStream` resolves the MIME type from the file extension with a fallback
  by `MediaType` (defaulting to `application/octet-stream`) and returns
  `Optional.of(file.sizeBytes())` in `MediaStream` to support HTTP range requests.

## Consequences

- Deactivated accounts lose API access on the next request without waiting for
  token expiry, and session lifetime is driven by `AppConfiguration`.
- Playback endpoints remain safe under HTTP `GET` semantics and tolerant of late
  client callbacks without turning them into server errors.
- `validateToken` performs one lookup in `IUserRepository` per authenticated
  request, which is cheap for OmniStream's scale.
- `CompositionRoot` must pass `config.tokenLifetime()` to `AccessService`, and
  `OmniStream-Services` (and `es/OmniStream-Servicios`) must add
  `tokenLifetime : Duration` (`vigenciaToken : Duration`) to `AccessService`.
