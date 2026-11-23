package co.omnistream.infrastructure.media;

import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.StreamProfile;
import co.omnistream.domain.port.out.ITranscoder;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Secondary adapter that converts a file on demand with FFmpeg (ADR 0015).
 *
 * <p>The conversion is written to a temporary file and read from there, so the client gets a stream
 * with a known size and can jump inside it. The file is deleted as soon as the stream is closed.
 */
public final class TranscodingAdapter implements ITranscoder {

  /** MIME of the container FFmpeg writes: fragmented MP4. */
  private static final String MIME = "video/mp4";

  private final Path ffmpeg;
  private final Path temporaryFolder;

  /**
   * @param ffmpeg the FFmpeg executable; a bare name is looked up in the {@code PATH}
   * @param temporaryFolder folder for the conversion before it is sent
   */
  public TranscodingAdapter(Path ffmpeg, Path temporaryFolder) {
    this.ffmpeg = Objects.requireNonNull(ffmpeg, "ffmpeg");
    this.temporaryFolder = Objects.requireNonNull(temporaryFolder, "temporaryFolder");
  }

  @Override
  public boolean supports(MediaType source, StreamProfile p) {
    Objects.requireNonNull(p, "p");
    return MediaTools.isAv(Objects.requireNonNull(source, "source"));
  }

  /**
   * Converts {@code path} from {@code pos} on, with the codec, bitrate and resolution {@code p}
   * asks for.
   */
  @Override
  public MediaStream transcode(Path path, StreamProfile p, Duration pos) {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(p, "p");
    Objects.requireNonNull(pos, "pos");
    if (pos.isNegative()) {
      throw new IllegalArgumentException("A position cannot be negative");
    }
    Path output = temporaryFile();
    try {
      MediaTools.runToFile(command(path, p, pos, output));
      long size = Files.size(output);
      return new MediaStream(
          new DeletingInputStream(Files.newInputStream(output), output),
          MIME,
          true,
          Optional.of(size));
    } catch (IOException e) {
      deleteQuietly(output);
      throw new MediaToolException("Cannot read the transcoded file", e);
    } catch (RuntimeException e) {
      deleteQuietly(output);
      throw e;
    }
  }

  /** The FFmpeg command that writes the requested profile to {@code output}. */
  private List<String> command(Path path, StreamProfile p, Duration pos, Path output) {
    List<String> command = new ArrayList<>();
    command.add(ffmpeg.toString());
    command.add("-hide_banner");
    command.add("-loglevel");
    command.add("error");
    command.add("-y");
    if (!pos.isZero()) {
      command.add("-ss");
      command.add(seconds(pos));
    }
    command.add("-i");
    command.add(path.toString());
    command.add("-map");
    command.add("0:v:0");
    command.add("-map");
    command.add("0:a:0?");
    command.add("-c:v");
    command.add(p.codec().orElse("libx264"));
    command.add("-preset");
    command.add("veryfast");
    p.bitrateKbps()
        .ifPresent(bitrate -> {
          command.add("-b:v");
          command.add(bitrate + "k");
        });
    p.resolution()
        .ifPresent(resolution -> {
          command.add("-vf");
          command.add("scale=-2:" + height(resolution));
        });
    command.add("-c:a");
    command.add("aac");
    command.add("-movflags");
    command.add("+frag_keyframe+empty_moov");
    command.add("-f");
    command.add("mp4");
    command.add(output.toString());
    return command;
  }

  /** The height of a profile such as {@code 720p}, or its last number. */
  static String height(String resolution) {
    String value = resolution.strip().toLowerCase(java.util.Locale.ROOT);
    int p = value.indexOf('p');
    String number = (p >= 0 ? value.substring(0, p) : value).replaceAll("[^0-9]", "");
    if (number.isEmpty()) {
      throw new IllegalArgumentException("Not a resolution: " + resolution);
    }
    return number;
  }

  private static String seconds(Duration pos) {
    return String.format(java.util.Locale.ROOT, "%.3f", pos.toNanos() / 1_000_000_000.0);
  }

  private Path temporaryFile() {
    try {
      Files.createDirectories(temporaryFolder);
      return Files.createTempFile(temporaryFolder, "omnistream-", ".mp4");
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot create a temporary file in " + temporaryFolder, e);
    }
  }

  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException ignored) {
      // Nothing else to do: the temporary folder is cleaned by the operating system
    }
  }

  /** Closes the stream and deletes the temporary file behind it. */
  private static final class DeletingInputStream extends InputStream {
    private final InputStream delegate;
    private final Path file;

    private DeletingInputStream(InputStream delegate, Path file) {
      this.delegate = delegate;
      this.file = file;
    }

    @Override
    public int read() throws IOException {
      return delegate.read();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      return delegate.read(buffer, offset, length);
    }

    @Override
    public void close() throws IOException {
      try {
        delegate.close();
      } finally {
        deleteQuietly(file);
      }
    }
  }
}
