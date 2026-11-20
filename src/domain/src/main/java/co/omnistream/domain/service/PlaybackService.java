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

  /**
   * Looks for the last session of {@code u} on {@code id}. If there is one and {@code canResume()}
   * is true, start a new session and {@code advance} it to that session's position; otherwise start
   * from zero, as {@link #openSession} does. A finished session is never reopened.
   */
  @Override
  public PlaybackSessionSnapshot resumeFromLastPosition(UserId u, ItemId id) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  /**
   * Every method that receives a {@code SessionId} first loads the session ({@code
   * ResourceNotFound} if it does not exist) and throws {@code AccessDenied} if its {@code userId}
   * is not {@code u}: a session is controlled only by the user who opened it, administrators
   * included.
   */
  @Override
  public MediaStream requestStream(UserId u, SessionId s, StreamProfile p) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  /**
   * {@code pos} is the absolute position the player reports, but the session moves by deltas:
   * {@code session.advance(pos.minus(session.toSnapshot().position()))}. The delta may be negative
   * (the user went back), and the session keeps the position between zero and the total duration.
   * On a finished session {@code advance}, {@code pause} and {@code resume} throw {@code
   * IllegalStateException}: check the status first, because the request can come late.
   */
  @Override
  public void recordProgress(UserId u, SessionId s, Duration pos) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void pauseSession(UserId u, SessionId s) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  /**
   * Goes on playing a paused session ({@code PlaybackSession.resume()}). Not to be confused with
   * {@link #resumeFromLastPosition}, which starts a new session where the last one stopped.
   */
  @Override
  public void resumeSession(UserId u, SessionId s) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void finishSession(UserId u, SessionId s) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  private StreamStrategy selectStream(MediaFileSnapshot a, StreamProfile p) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
