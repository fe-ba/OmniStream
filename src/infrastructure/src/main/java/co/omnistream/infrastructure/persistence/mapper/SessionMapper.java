package co.omnistream.infrastructure.persistence.mapper;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.playback.SessionId;
import co.omnistream.domain.model.playback.SessionStatus;
import co.omnistream.domain.snapshot.PlaybackSessionSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Reads a row of {@code playback_sessions} (Data Mapper). */
public final class SessionMapper {

  /** The playback session of the current row. */
  public PlaybackSessionSnapshot toSnapshot(ResultSet row) throws SQLException {
    return new PlaybackSessionSnapshot(
        new SessionId(UUID.fromString(row.getString("id"))),
        new ItemId(UUID.fromString(row.getString("file_id"))),
        new UserId(UUID.fromString(row.getString("user_id"))),
        Duration.ofNanos(row.getLong("position_nanos")),
        Duration.ofNanos(row.getLong("total_nanos")),
        SessionStatus.valueOf(row.getString("status")),
        Instant.ofEpochMilli(row.getLong("started_at")));
  }
}
