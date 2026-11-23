package co.omnistream.infrastructure.media;

import co.omnistream.domain.model.catalog.MediaType;
import java.util.List;
import java.util.Locale;

/** Helpers shared by the adapters that call FFmpeg and FFprobe. */
final class MediaTools {

  /** How long a tool may take before it is killed. */
  static final long TIMEOUT_SECONDS = 120;

  private MediaTools() {}

  /** Whether a tool is audio or video, the two kinds FFprobe understands. */
  static boolean isAv(MediaType type) {
    return type == MediaType.AUDIO || type == MediaType.VIDEO;
  }

  /** The extension of a file name in lower case, without the dot. */
  static String extension(String name) {
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  /** Runs a command and returns its standard output; a non-zero exit is an error. */
  static String run(List<String> command) {
    ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
    try {
      Process process = builder.start();
      byte[] output = process.getInputStream().readAllBytes();
      if (!process.waitFor(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new MediaToolException(command.getFirst() + " took too long");
      }
      if (process.exitValue() != 0) {
        throw new MediaToolException(
            command.getFirst()
                + " failed with exit code "
                + process.exitValue()
                + ": "
                + new String(output, java.nio.charset.StandardCharsets.UTF_8).strip());
      }
      return new String(output, java.nio.charset.StandardCharsets.UTF_8);
    } catch (java.io.IOException e) {
      throw new MediaToolException("Cannot run " + command.getFirst() + "; is it installed?", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MediaToolException("Interrupted while running " + command.getFirst(), e);
    }
  }

  /** Runs a command whose output goes to a file; a non-zero exit is an error. */
  static void runToFile(List<String> command) {
    ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
    try {
      Process process = builder.start();
      byte[] output = process.getInputStream().readAllBytes();
      if (!process.waitFor(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new MediaToolException(command.getFirst() + " took too long");
      }
      if (process.exitValue() != 0) {
        throw new MediaToolException(
            command.getFirst()
                + " failed with exit code "
                + process.exitValue()
                + ": "
                + new String(output, java.nio.charset.StandardCharsets.UTF_8).strip());
      }
    } catch (java.io.IOException e) {
      throw new MediaToolException("Cannot run " + command.getFirst() + "; is it installed?", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MediaToolException("Interrupted while running " + command.getFirst(), e);
    }
  }
}
