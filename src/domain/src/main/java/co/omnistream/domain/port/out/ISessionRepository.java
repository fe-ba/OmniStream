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

  Optional<PlaybackSession> findLastSession(UserId u, ItemId a);

  int countActive();
}
