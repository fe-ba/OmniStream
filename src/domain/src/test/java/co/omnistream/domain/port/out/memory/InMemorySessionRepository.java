package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.playback.PlaybackSession;
import co.omnistream.domain.model.playback.SessionId;
import co.omnistream.domain.model.playback.SessionStatus;
import co.omnistream.domain.port.out.ISessionRepository;
import co.omnistream.domain.snapshot.PlaybackSessionSnapshot;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** In-memory playback session repository for the domain tests (ADR 0018). */
public final class InMemorySessionRepository implements ISessionRepository {

  private final Map<SessionId, PlaybackSessionSnapshot> sessions = new LinkedHashMap<>();

  @Override
  public void save(PlaybackSession session) {
    Objects.requireNonNull(session, "session");
    sessions.put(session.toSnapshot().id(), session.toSnapshot());
  }

  @Override
  public Optional<PlaybackSession> findById(SessionId id) {
    Objects.requireNonNull(id, "id");
    return Optional.ofNullable(sessions.get(id)).map(PlaybackSession::fromSnapshot);
  }

  @Override
  public Optional<PlaybackSession> findLastSession(UserId user, ItemId file) {
    Objects.requireNonNull(user, "user");
    Objects.requireNonNull(file, "file");
    return sessions.values().stream()
        .filter(session -> session.userId().equals(user) && session.fileId().equals(file))
        .max(Comparator.comparing(PlaybackSessionSnapshot::startedAt))
        .map(PlaybackSession::fromSnapshot);
  }

  @Override
  public int countActive() {
    return (int)
        sessions.values().stream()
            .filter(session ->
                session.status() == SessionStatus.ACTIVE
                    || session.status() == SessionStatus.PAUSED)
            .count();
  }
}
