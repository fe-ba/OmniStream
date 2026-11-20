package co.omnistream.domain.port.out;

import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.StreamProfile;
import java.nio.file.Path;
import java.time.Duration;

/** Driven port: defined by the domain, implemented by secondary adapters. */
public interface ITranscoder {
  boolean supports(MediaType source, StreamProfile p);

  MediaStream transcode(Path path, StreamProfile p, Duration pos);
}
