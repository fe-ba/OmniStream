package co.omnistream.infrastructure.media;

/** An external media tool (FFmpeg, FFprobe, ExifTool) failed or is not installed. */
public final class MediaToolException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public MediaToolException(String message) {
    super(message);
  }

  public MediaToolException(String message, Throwable cause) {
    super(message, cause);
  }
}
