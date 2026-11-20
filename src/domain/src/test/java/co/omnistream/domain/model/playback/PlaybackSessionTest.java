package co.omnistream.domain.model.playback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.snapshot.PlaybackSessionSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlaybackSessionTest {

  private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
  private static final Duration HOUR = Duration.ofHours(1);

  private final ItemId file = new ItemId(UUID.randomUUID());
  private final UserId user = new UserId(UUID.randomUUID());

  private PlaybackSession anHourLong() {
    return PlaybackSession.start(file, user, HOUR, NOW);
  }

  // ---------------------------------------------------------------- start

  @Test
  void aNewSessionIsActiveAtTheBeginning() {
    PlaybackSessionSnapshot session = anHourLong().toSnapshot();

    assertThat(session.fileId()).isEqualTo(file);
    assertThat(session.userId()).isEqualTo(user);
    assertThat(session.position()).isEqualTo(Duration.ZERO);
    assertThat(session.totalDuration()).isEqualTo(HOUR);
    assertThat(session.status()).isEqualTo(SessionStatus.ACTIVE);
    assertThat(session.startedAt()).isEqualTo(NOW);
  }

  @Test
  void aSessionCannotHaveANegativeDuration() {
    assertThatThrownBy(() -> PlaybackSession.start(file, user, Duration.ofSeconds(-1), NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aSessionNeedsAFileAUserAndAnInstant() {
    assertThatThrownBy(() -> PlaybackSession.start(null, user, HOUR, NOW))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> PlaybackSession.start(file, null, HOUR, NOW))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> PlaybackSession.start(file, user, HOUR, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void sessionIdNeedsAUuid() {
    assertThatThrownBy(() -> new SessionId(null)).isInstanceOf(NullPointerException.class);
  }

  // ---------------------------------------------------------------- position

  @Test
  void advancingMovesThePosition() {
    PlaybackSession session = anHourLong();

    session.advance(Duration.ofMinutes(10));
    session.advance(Duration.ofMinutes(5));

    assertThat(session.toSnapshot().position()).isEqualTo(Duration.ofMinutes(15));
  }

  @Test
  void aNegativeDeltaGoesBack() {
    PlaybackSession session = anHourLong();
    session.advance(Duration.ofMinutes(10));

    session.advance(Duration.ofMinutes(-4));

    assertThat(session.toSnapshot().position()).isEqualTo(Duration.ofMinutes(6));
  }

  @Test
  void thePositionDoesNotGoPastTheEnd() {
    PlaybackSession session = anHourLong();

    session.advance(Duration.ofMinutes(75));

    assertThat(session.toSnapshot().position()).isEqualTo(HOUR);
  }

  @Test
  void thePositionDoesNotGoBelowZero() {
    PlaybackSession session = anHourLong();
    session.advance(Duration.ofMinutes(1));

    session.advance(Duration.ofMinutes(-5));

    assertThat(session.toSnapshot().position()).isEqualTo(Duration.ZERO);
  }

  @Test
  void aPausedSessionCanStillMove() {
    PlaybackSession session = anHourLong();
    session.pause();

    session.advance(Duration.ofMinutes(3));

    assertThat(session.toSnapshot().position()).isEqualTo(Duration.ofMinutes(3));
  }

  // ---------------------------------------------------------------- status

  @Test
  void aSessionPausesAndResumes() {
    PlaybackSession session = anHourLong();

    session.pause();
    assertThat(session.toSnapshot().status()).isEqualTo(SessionStatus.PAUSED);

    session.resume();
    assertThat(session.toSnapshot().status()).isEqualTo(SessionStatus.ACTIVE);
  }

  @Test
  void pausingTwiceAndResumingAnActiveSessionDoNothing() {
    PlaybackSession session = anHourLong();

    session.resume();
    assertThat(session.toSnapshot().status()).isEqualTo(SessionStatus.ACTIVE);

    session.pause();
    session.pause();
    assertThat(session.toSnapshot().status()).isEqualTo(SessionStatus.PAUSED);
  }

  @Test
  void aFinishedSessionKeepsItsPositionAndDoesNotChangeAnyMore() {
    PlaybackSession session = anHourLong();
    session.advance(Duration.ofMinutes(20));

    session.finish();
    session.finish();

    assertThat(session.toSnapshot().status()).isEqualTo(SessionStatus.FINISHED);
    assertThat(session.toSnapshot().position()).isEqualTo(Duration.ofMinutes(20));
    assertThatThrownBy(() -> session.advance(Duration.ofMinutes(1)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(session::pause).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(session::resume).isInstanceOf(IllegalStateException.class);
  }

  // ---------------------------------------------------------------- progress

  @Test
  void percentWatchedFollowsThePosition() {
    PlaybackSession session = anHourLong();
    assertThat(session.percentWatched()).isEqualTo(0.0);

    session.advance(Duration.ofMinutes(15));
    assertThat(session.percentWatched()).isEqualTo(25.0);

    session.advance(HOUR);
    assertThat(session.percentWatched()).isEqualTo(100.0);
  }

  @Test
  void aFileWithoutDurationIsWatchedAsSoonAsItIsOpened() {
    PlaybackSession image = PlaybackSession.start(file, user, Duration.ZERO, NOW);

    assertThat(image.percentWatched()).isEqualTo(100.0);
    assertThat(image.isCompleted()).isTrue();
    assertThat(image.canResume()).isFalse();
  }

  @Test
  void aSessionIsCompletedFromNinetyFivePercentOn() {
    PlaybackSession session = PlaybackSession.start(file, user, Duration.ofMinutes(100), NOW);

    session.advance(Duration.ofMinutes(94));
    assertThat(session.isCompleted()).isFalse();

    session.advance(Duration.ofMinutes(1));
    assertThat(session.isCompleted()).isTrue();
  }

  @Test
  void aSessionThatGotSomewhereButNotToTheEndCanBeResumed() {
    PlaybackSession session = anHourLong();
    assertThat(session.canResume()).isFalse();

    session.advance(Duration.ofMinutes(20));
    session.finish();
    assertThat(session.canResume()).isTrue();
  }

  @Test
  void aCompletedSessionCannotBeResumed() {
    PlaybackSession session = anHourLong();

    session.advance(Duration.ofMinutes(59));

    assertThat(session.canResume()).isFalse();
  }

  // ---------------------------------------------------------------- snapshots and identity

  @Test
  void aSessionRebuiltFromItsSnapshotIsTheSame() {
    PlaybackSession session = anHourLong();
    session.advance(Duration.ofMinutes(12));
    session.pause();

    PlaybackSession rebuilt = PlaybackSession.fromSnapshot(session.toSnapshot());

    assertThat(rebuilt).isEqualTo(session);
    assertThat(rebuilt.toSnapshot()).isEqualTo(session.toSnapshot());
  }

  @Test
  void eachSessionGetsItsOwnIdentifier() {
    assertThat(anHourLong()).isNotEqualTo(anHourLong());
  }
}
