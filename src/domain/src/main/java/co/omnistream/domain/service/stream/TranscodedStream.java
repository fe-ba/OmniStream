package co.omnistream.domain.service.stream;

import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.StreamProfile;
import co.omnistream.domain.port.out.ITranscoder;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.time.Duration;

/** Concrete strategy: transcodes the file on demand (Strategy). */
public final class TranscodedStream implements StreamStrategy {
  private final ITranscoder transcoder;

  public TranscodedStream(ITranscoder transcoder) {
    this.transcoder = transcoder;
  }

  @Override
  public boolean supports(MediaFileSnapshot a, StreamProfile p) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public MediaStream generateStream(MediaFileSnapshot a, StreamProfile p, Duration pos) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
