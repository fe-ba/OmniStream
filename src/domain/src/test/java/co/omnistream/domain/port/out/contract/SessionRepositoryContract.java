package co.omnistream.domain.port.out.contract;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.playback.PlaybackSession;
import co.omnistream.domain.port.out.ISessionRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behaviour every {@link ISessionRepository} must share (ADR 0018). */
public abstract class SessionRepositoryContract {

  protected abstract ISessionRepository createRepository();

  @Test
  void findsASavedSessionById() {
    ISessionRepository repository = createRepository();
    PlaybackSession session = session(PortFixtures.NOW);

    repository.save(session);

    assertThat(repository.findById(session.toSnapshot().id())).contains(session);
  }

  @Test
  void findsTheLatestSessionOfAUserOnAFile() {
    ISessionRepository repository = createRepository();
    UserId user = new UserId(UUID.randomUUID());
    ItemId file = new ItemId(UUID.randomUUID());
    PlaybackSession earlier = session(user, file, PortFixtures.NOW);
    PlaybackSession later = session(user, file, PortFixtures.NOW.plusSeconds(60));
    repository.save(earlier);
    repository.save(later);

    assertThat(repository.findLastSession(user, file)).contains(later);
  }

  @Test
  void countsTheActiveAndPausedSessions() {
    ISessionRepository repository = createRepository();
    PlaybackSession active = session(PortFixtures.NOW);
    PlaybackSession paused = session(PortFixtures.NOW.plusSeconds(1));
    PlaybackSession finished = session(PortFixtures.NOW.plusSeconds(2));
    paused.pause();
    finished.finish();
    repository.save(active);
    repository.save(paused);
    repository.save(finished);

    assertThat(repository.countActive()).isEqualTo(2);

    active.finish();
    repository.save(active);

    assertThat(repository.countActive()).isEqualTo(1);
  }

  @Test
  void updatesASavedSession() {
    ISessionRepository repository = createRepository();
    PlaybackSession session = session(PortFixtures.NOW);
    repository.save(session);

    session.advance(Duration.ofSeconds(30));
    repository.save(session);

    assertThat(repository.findById(session.toSnapshot().id()))
        .map(saved -> saved.toSnapshot().position())
        .contains(Duration.ofSeconds(30));
  }

  private static PlaybackSession session(java.time.Instant startedAt) {
    return session(new UserId(UUID.randomUUID()), new ItemId(UUID.randomUUID()), startedAt);
  }

  private static PlaybackSession session(UserId user, ItemId file, java.time.Instant startedAt) {
    return PlaybackSession.start(file, user, Duration.ofMinutes(90), startedAt);
  }
}
