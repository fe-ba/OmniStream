package co.omnistream.domain.port.dto;

import java.util.Objects;
import java.util.Optional;

/**
 * What a client can play. A client that leaves everything out accepts the file as it is (direct
 * play); one that asks for a codec, bitrate or resolution may need it transcoded.
 *
 * @param codec the codec it wants, for example {@code h264}
 * @param bitrateKbps the highest bitrate it wants, in kilobits per second
 * @param resolution the highest resolution it wants, for example {@code 720p}
 */
public record StreamProfile(
    Optional<String> codec, Optional<Integer> bitrateKbps, Optional<String> resolution) {

  public StreamProfile {
    Objects.requireNonNull(codec, "codec");
    Objects.requireNonNull(bitrateKbps, "bitrateKbps");
    Objects.requireNonNull(resolution, "resolution");
    if (bitrateKbps.isPresent() && bitrateKbps.get() <= 0) {
      throw new IllegalArgumentException("A bitrate must be positive");
    }
  }

  /** Whether the client asked for nothing in particular, so the file can be sent as it is. */
  public boolean isDirectPlay() {
    return codec.isEmpty() && bitrateKbps.isEmpty() && resolution.isEmpty();
  }
}
