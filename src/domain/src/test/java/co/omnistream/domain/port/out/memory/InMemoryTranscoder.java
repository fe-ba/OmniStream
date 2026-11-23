package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.StreamProfile;
import co.omnistream.domain.port.out.ITranscoder;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory transcoder for the domain tests (ADR 0018).
 *
 * <p>Every conversion answers the same bytes, without running FFmpeg. It supports the media types
 * it was configured with.
 */
public final class InMemoryTranscoder implements ITranscoder {

  private final byte[] data;
  private final String mime;
  private final boolean transcoded;
  private final Optional<Long> size;
  private final Set<MediaType> supported;

  /** A video transcoder that answers {@code data} as a fragmented MP4. */
  public InMemoryTranscoder(byte[] data) {
    this(data, "video/mp4", true, Optional.of((long) data.length), MediaType.VIDEO);
  }

  public InMemoryTranscoder(
      byte[] data, String mime, boolean transcoded, Optional<Long> size, MediaType... supported) {
    this.data = Objects.requireNonNull(data, "data").clone();
    this.mime = Objects.requireNonNull(mime, "mime");
    this.transcoded = transcoded;
    this.size = Objects.requireNonNull(size, "size");
    this.supported = Set.of(supported);
  }

  @Override
  public boolean supports(MediaType source, StreamProfile profile) {
    Objects.requireNonNull(profile, "profile");
    return supported.contains(Objects.requireNonNull(source, "source"));
  }

  @Override
  public MediaStream transcode(Path path, StreamProfile profile, Duration position) {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(position, "position");
    return new MediaStream(new ByteArrayInputStream(data.clone()), mime, transcoded, size);
  }
}
