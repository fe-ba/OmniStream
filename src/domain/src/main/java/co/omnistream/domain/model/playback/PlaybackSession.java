package co.omnistream.domain.model.playback;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.snapshot.PlaybackSessionSnapshot;
import java.time.Duration;
import java.time.Instant;

/** Aggregate root. */
public final class PlaybackSession {
  private SessionId id;
  private ItemId fileId;
  private UserId userId;
  private Duration position;
  private Duration totalDuration;
  private SessionStatus status;
  private Instant startedAt;

  private PlaybackSession() {}

  public static PlaybackSession start(
      ItemId fileId, UserId userId, Duration totalDuration, Instant now) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void advance(Duration delta) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void pause() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void resume() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void finish() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public boolean isCompleted() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public boolean canResume() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public double percentWatched() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public PlaybackSessionSnapshot toSnapshot() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public static PlaybackSession fromSnapshot(PlaybackSessionSnapshot i) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
