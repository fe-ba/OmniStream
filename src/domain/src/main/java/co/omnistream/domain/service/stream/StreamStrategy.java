package co.omnistream.domain.service.stream;

import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.StreamProfile;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.time.Duration;

/** Strategy that turns a file into a stream for a device profile (Strategy). */
public interface StreamStrategy {
  boolean supports(MediaFileSnapshot a, StreamProfile p);

  MediaStream generateStream(MediaFileSnapshot a, StreamProfile p, Duration pos);
}
