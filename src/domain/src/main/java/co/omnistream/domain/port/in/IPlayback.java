package co.omnistream.domain.port.in;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.playback.SessionId;
import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.StreamProfile;
import co.omnistream.domain.snapshot.PlaybackSessionSnapshot;
import java.time.Duration;

/** Driving port: implemented by a domain service, called by primary adapters. */
public interface IPlayback {
  PlaybackSessionSnapshot openSession(UserId u, ItemId id);

  PlaybackSessionSnapshot resumeFromLastPosition(UserId u, ItemId id);

  MediaStream requestStream(UserId u, SessionId s, StreamProfile p);

  void recordProgress(UserId u, SessionId s, Duration pos);

  void pauseSession(UserId u, SessionId s);

  void resumeSession(UserId u, SessionId s);

  void finishSession(UserId u, SessionId s);
}
