package co.omnistream.domain.service;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.playback.SessionId;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.StreamProfile;
import co.omnistream.domain.port.in.IPlayback;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.ISessionRepository;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.service.stream.StreamStrategy;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import co.omnistream.domain.snapshot.PlaybackSessionSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/** Domain service that implements the IPlayback driving port. */
public final class PlaybackService extends AuthorizedService implements IPlayback {
  private final ISessionRepository sessionRepository;
  private final ICatalogRepository catalogRepository;
  private final List<StreamStrategy> streamStrategies;

  public PlaybackService(
      IUserRepository userRepository,
      AccessPolicy accessPolicy,
      Clock clock,
      ISessionRepository sessionRepository,
      ICatalogRepository catalogRepository,
      List<StreamStrategy> streamStrategies) {
    super(userRepository, accessPolicy, clock);
    this.sessionRepository = sessionRepository;
    this.catalogRepository = catalogRepository;
    this.streamStrategies = streamStrategies;
  }

  @Override
  public PlaybackSessionSnapshot openSession(UserId u, ItemId id) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public PlaybackSessionSnapshot resumeFromLastPosition(UserId u, ItemId id) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public MediaStream requestStream(SessionId s, StreamProfile p) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void recordProgress(SessionId s, Duration pos) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void pauseSession(SessionId s) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void finishSession(SessionId s) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  private StreamStrategy selectStream(MediaFileSnapshot a, StreamProfile p) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
