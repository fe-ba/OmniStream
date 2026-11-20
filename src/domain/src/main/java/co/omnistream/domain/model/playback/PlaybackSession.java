package co.omnistream.domain.model.playback;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.snapshot.PlaybackSessionSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Root of the playback aggregate: one user playing one file, and how far they got.
 *
 * <p>It refers to the file and to the user by identifier, so it is loaded and saved on its own.
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>The position is never below zero nor beyond the total duration.
 *   <li>A {@link SessionStatus#FINISHED} session does not change any more. To keep watching, the
 *       service starts a new session and moves it to the position of the last one.
 * </ul>
 */
public final class PlaybackSession {

  /** Share of the content that counts as watched: the credits do not need to be seen. */
  static final double COMPLETED_PERCENT = 95.0;

  private final SessionId id;
  private final ItemId fileId;
  private final UserId userId;
  private Duration position;
  private final Duration totalDuration;
  private SessionStatus status;
  private final Instant startedAt;

  private PlaybackSession(
      SessionId id,
      ItemId fileId,
      UserId userId,
      Duration position,
      Duration totalDuration,
      SessionStatus status,
      Instant startedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.fileId = Objects.requireNonNull(fileId, "fileId");
    this.userId = Objects.requireNonNull(userId, "userId");
    this.position = Objects.requireNonNull(position, "position");
    this.totalDuration = Objects.requireNonNull(totalDuration, "totalDuration");
    this.status = Objects.requireNonNull(status, "status");
    this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
  }

  /**
   * A new active session at the beginning of the file.
   *
   * @param totalDuration length of the file; {@link Duration#ZERO} for images and documents
   * @throws IllegalArgumentException if {@code totalDuration} is negative
   */
  public static PlaybackSession start(
      ItemId fileId, UserId userId, Duration totalDuration, Instant now) {
    if (Objects.requireNonNull(totalDuration, "totalDuration").isNegative()) {
      throw new IllegalArgumentException("A file cannot have a negative duration");
    }
    return new PlaybackSession(
        new SessionId(UUID.randomUUID()),
        fileId,
        userId,
        Duration.ZERO,
        totalDuration,
        SessionStatus.ACTIVE,
        now);
  }

  /**
   * Moves the position by {@code delta}: forward, or backward if it is negative (the user went
   * back). The result is kept between zero and the total duration, because players report positions
   * with some error.
   *
   * @throws IllegalStateException if the session is finished
   */
  public void advance(Duration delta) {
    Objects.requireNonNull(delta, "delta");
    requireNotFinished("move");
    Duration moved = position.plus(delta);
    if (moved.isNegative()) {
      position = Duration.ZERO;
    } else if (moved.compareTo(totalDuration) > 0) {
      position = totalDuration;
    } else {
      position = moved;
    }
  }

  /**
   * Pauses playback. Pausing a paused session does nothing.
   *
   * @throws IllegalStateException if the session is finished
   */
  public void pause() {
    requireNotFinished("pause");
    status = SessionStatus.PAUSED;
  }

  /**
   * Goes on playing after a pause. Resuming an active session does nothing.
   *
   * @throws IllegalStateException if the session is finished
   */
  public void resume() {
    requireNotFinished("resume");
    status = SessionStatus.ACTIVE;
  }

  /** Closes the session and keeps its position. Finishing it again does nothing. */
  public void finish() {
    status = SessionStatus.FINISHED;
  }

  /**
   * Whether the user watched the file: at least {@value #COMPLETED_PERCENT} % of it, whether the
   * session is still open or not.
   */
  public boolean isCompleted() {
    return percentWatched() >= COMPLETED_PERCENT;
  }

  /**
   * Whether a new session should start where this one stopped: it got somewhere, not to the end.
   */
  public boolean canResume() {
    return position.isPositive() && !isCompleted();
  }

  /**
   * How much of the file has been played, from 0 to 100. A file without duration (an image or a
   * document) counts as fully watched as soon as it is opened.
   */
  public double percentWatched() {
    if (totalDuration.isZero()) {
      return 100.0;
    }
    return position.toNanos() * 100.0 / totalDuration.toNanos();
  }

  /** The session's current state (Memento, ADR 0011). */
  public PlaybackSessionSnapshot toSnapshot() {
    return new PlaybackSessionSnapshot(
        id, fileId, userId, position, totalDuration, status, startedAt);
  }

  /** Rebuilds a stored session exactly as it was, without checking the creation rules. */
  public static PlaybackSession fromSnapshot(PlaybackSessionSnapshot i) {
    Objects.requireNonNull(i, "i");
    return new PlaybackSession(
        i.id(), i.fileId(), i.userId(), i.position(), i.totalDuration(), i.status(), i.startedAt());
  }

  /** Two sessions are the same if they have the same identifier. */
  @Override
  public boolean equals(Object other) {
    return other instanceof PlaybackSession session && id.equals(session.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  private void requireNotFinished(String action) {
    if (status == SessionStatus.FINISHED) {
      throw new IllegalStateException("Cannot " + action + " a finished session");
    }
  }
}
