package co.omnistream.domain.port.out;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.playback.PlaybackSession;
import co.omnistream.domain.model.playback.SessionId;
import java.util.Optional;

/** Driven port: defined by the domain, implemented by secondary adapters. */
public interface ISessionRepository {
  void save(PlaybackSession s);

  Optional<PlaybackSession> findById(SessionId id);

  /** The most recent session (latest {@code startedAt}) of {@code u} on file {@code a}. */
  Optional<PlaybackSession> findLastSession(UserId u, ItemId a);

  /** Sessions that are not finished: those in {@code ACTIVE} or {@code PAUSED} status. */
  int countActive();
}
