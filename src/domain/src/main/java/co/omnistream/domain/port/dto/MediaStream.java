package co.omnistream.domain.port.dto;

import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

/**
 * Media ready to send to a client. Whoever receives it closes {@code data}.
 *
 * @param data the bytes
 * @param mime their type, for example {@code video/mp4}
 * @param transcoded whether they were converted on the fly
 * @param sizeBytes how many bytes there are, when it is known in advance: a file sent as it is has
 *     a size, so the client can jump to any position (HTTP range requests); a stream being
 *     transcoded does not
 */
public record MediaStream(
    InputStream data, String mime, boolean transcoded, Optional<Long> sizeBytes) {

  public MediaStream {
    Objects.requireNonNull(data, "data");
    Objects.requireNonNull(mime, "mime");
    Objects.requireNonNull(sizeBytes, "sizeBytes");
  }
}
