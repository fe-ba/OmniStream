package co.omnistream.domain.snapshot;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.playback.SessionId;
import co.omnistream.domain.model.playback.SessionStatus;
import java.time.Duration;
import java.time.Instant;

/** Immutable snapshot of an aggregate's state (Memento, ADR 0011). */
public record PlaybackSessionSnapshot(
    SessionId id,
    ItemId fileId,
    UserId userId,
    Duration position,
    Duration totalDuration,
    SessionStatus status,
    Instant startedAt) {}
