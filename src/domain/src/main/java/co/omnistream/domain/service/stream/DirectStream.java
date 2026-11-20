package co.omnistream.domain.service.stream;

import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.StreamProfile;
import co.omnistream.domain.port.out.IFileStorage;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.time.Duration;

/** Concrete strategy: plays the file as it is (Strategy). */
public final class DirectStream implements StreamStrategy {
  private final IFileStorage storage;

  public DirectStream(IFileStorage storage) {
    this.storage = storage;
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
