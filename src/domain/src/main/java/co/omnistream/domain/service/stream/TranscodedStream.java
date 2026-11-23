package co.omnistream.domain.service.stream;

import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.StreamProfile;
import co.omnistream.domain.port.out.ITranscoder;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.time.Duration;
import java.util.Objects;

/** Concrete strategy: transcodes the file on demand (Strategy). */
public final class TranscodedStream implements StreamStrategy {
  private final ITranscoder transcoder;

  public TranscodedStream(ITranscoder transcoder) {
    this.transcoder = Objects.requireNonNull(transcoder, "transcoder");
  }

  @Override
  public boolean supports(MediaFileSnapshot a, StreamProfile p) {
    Objects.requireNonNull(a, "file");
    Objects.requireNonNull(p, "profile");
    return transcoder.supports(a.type(), p);
  }

  @Override
  public MediaStream generateStream(MediaFileSnapshot a, StreamProfile p, Duration pos) {
    Objects.requireNonNull(a, "file");
    Objects.requireNonNull(p, "profile");
    Objects.requireNonNull(pos, "position");
    return transcoder.transcode(a.path(), p, pos);
  }
}
