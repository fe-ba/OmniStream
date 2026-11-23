package co.omnistream.domain.service;

import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.FileRejected;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.playback.PlaybackSession;
import co.omnistream.domain.model.playback.SessionId;
import co.omnistream.domain.model.playback.SessionStatus;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.StreamProfile;
import co.omnistream.domain.port.in.IPlayback;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.ISessionRepository;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.service.stream.StreamStrategy;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import co.omnistream.domain.snapshot.PlaybackSessionSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Domain service that implements the {@link IPlayback} driving port and acts as the context of the
 * stream {@link StreamStrategy} (Strategy).
 */
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
    this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository");
    this.catalogRepository = Objects.requireNonNull(catalogRepository, "catalogRepository");
    this.streamStrategies =
        List.copyOf(Objects.requireNonNull(streamStrategies, "streamStrategies"));
  }

  @Override
  public PlaybackSessionSnapshot openSession(UserId u, ItemId id) {
    Objects.requireNonNull(id, "id");
    CatalogSnapshot catalog = catalogRepository.getCatalog().toSnapshot();
    checkAccess(u, id, AccessType.VIEW, catalog);
    MediaFileSnapshot file = loadFile(id);
    PlaybackSession session = PlaybackSession.start(id, u, file.duration(), clock.instant());
    sessionRepository.save(session);
    return session.toSnapshot();
  }

  /**
   * Looks for the last session of {@code u} on {@code id}. If there is one and {@code canResume()}
   * is true, start a new session and {@code advance} it to that session's position; otherwise start
   * from zero, as {@link #openSession} does. A finished session is never reopened.
   */
  @Override
  public PlaybackSessionSnapshot resumeFromLastPosition(UserId u, ItemId id) {
    Objects.requireNonNull(id, "id");
    CatalogSnapshot catalog = catalogRepository.getCatalog().toSnapshot();
    checkAccess(u, id, AccessType.VIEW, catalog);
    MediaFileSnapshot file = loadFile(id);
    PlaybackSession session = PlaybackSession.start(id, u, file.duration(), clock.instant());
    sessionRepository
        .findLastSession(u, id)
        .filter(PlaybackSession::canResume)
        .ifPresent(last -> session.advance(last.toSnapshot().position()));
    sessionRepository.save(session);
    return session.toSnapshot();
  }

  /**
   * Every method that receives a {@code SessionId} first loads the session ({@code
   * ResourceNotFound} if it does not exist) and throws {@code AccessDenied} if its {@code userId}
   * is not {@code u}: a session is controlled only by the user who opened it, administrators
   * included.
   */
  @Override
  public MediaStream requestStream(UserId u, SessionId s, StreamProfile p) {
    Objects.requireNonNull(p, "profile");
    PlaybackSession session = loadOwnedSession(u, s);
    PlaybackSessionSnapshot snap = session.toSnapshot();
    if (snap.status() == SessionStatus.FINISHED) {
      throw new AccessDenied("Cannot stream a finished session: " + s);
    }
    CatalogSnapshot catalog = catalogRepository.getCatalog().toSnapshot();
    checkAccess(u, snap.fileId(), AccessType.VIEW, catalog);
    MediaFileSnapshot file = loadFile(snap.fileId());
    return selectStream(file, p).generateStream(file, p, snap.position());
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
    Objects.requireNonNull(pos, "position");
    if (pos.isNegative()) {
      throw new IllegalArgumentException("Playback position cannot be negative");
    }
    PlaybackSession session = loadOwnedSession(u, s);
    PlaybackSessionSnapshot snap = session.toSnapshot();
    if (snap.status() == SessionStatus.FINISHED) {
      return;
    }
    session.advance(pos.minus(snap.position()));
    sessionRepository.save(session);
  }

  @Override
  public void pauseSession(UserId u, SessionId s) {
    PlaybackSession session = loadOwnedSession(u, s);
    if (session.toSnapshot().status() == SessionStatus.FINISHED) {
      return;
    }
    session.pause();
    sessionRepository.save(session);
  }

  /**
   * Goes on playing a paused session ({@code PlaybackSession.resume()}). Not to be confused with
   * {@link #resumeFromLastPosition}, which starts a new session where the last one stopped.
   */
  @Override
  public void resumeSession(UserId u, SessionId s) {
    PlaybackSession session = loadOwnedSession(u, s);
    session.resume();
    sessionRepository.save(session);
  }

  @Override
  public void finishSession(UserId u, SessionId s) {
    PlaybackSession session = loadOwnedSession(u, s);
    session.finish();
    sessionRepository.save(session);
  }

  private PlaybackSession loadOwnedSession(UserId u, SessionId s) {
    Objects.requireNonNull(s, "sessionId");
    User user = loadUser(u);
    if (!user.toSnapshot().active()) {
      throw new AccessDenied("User account is deactivated");
    }
    PlaybackSession session =
        sessionRepository
            .findById(s)
            .orElseThrow(() -> new ResourceNotFound("Session not found: " + s));
    if (!session.toSnapshot().userId().equals(u)) {
      throw new AccessDenied("Session " + s + " does not belong to user " + u);
    }
    return session;
  }

  private MediaFileSnapshot loadFile(ItemId id) {
    return catalogRepository
        .findFile(id)
        .orElseThrow(() -> new ResourceNotFound("File not found: " + id));
  }

  private StreamStrategy selectStream(MediaFileSnapshot a, StreamProfile p) {
    for (StreamStrategy strategy : streamStrategies) {
      if (strategy.supports(a, p)) {
        return strategy;
      }
    }
    throw new FileRejected("No stream strategy supports profile " + p + " for " + a.id());
  }
}
