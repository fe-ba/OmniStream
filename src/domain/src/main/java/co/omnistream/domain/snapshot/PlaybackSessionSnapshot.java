package co.omnistream.domain.snapshot;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.playback.SessionId;
import co.omnistream.domain.model.playback.SessionStatus;
import java.time.Duration;
import java.time.Instant;

/**
 * State of a {@code PlaybackSession} (Memento, ADR 0011).
 *
 * @param id the session
 * @param fileId the file being played
 * @param userId who plays it
 * @param position how far playback got, between zero and {@code totalDuration}
 * @param totalDuration length of the file
 * @param status playing, paused or finished
 * @param startedAt when the session started
 */
public record PlaybackSessionSnapshot(
    SessionId id,
    ItemId fileId,
    UserId userId,
    Duration position,
    Duration totalDuration,
    SessionStatus status,
    Instant startedAt) {}
