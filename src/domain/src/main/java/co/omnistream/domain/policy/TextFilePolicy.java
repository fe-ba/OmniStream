package co.omnistream.domain.policy;

import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Decides which files can be edited as text (ADR 0027): plain text, Markdown, subtitles,
 * configuration and source code, recognized by the extension of the stored file, of at most {@value
 * #MAX_BYTES} bytes and encoded in UTF-8. Videos, audio, images and PDFs are never text, whatever
 * their name.
 *
 * <p>Stateless, like {@link DeduplicationPolicy}.
 */
public final class TextFilePolicy {

  /** Largest text file that can be edited: 1 MiB. */
  public static final int MAX_BYTES = 1024 * 1024;

  /** Extensions of the files that are edited as text, in lower case. */
  public static final Set<String> EXTENSIONS =
      Set.of(
          // plain text and notes
          "txt",
          "text",
          "md",
          "markdown",
          "log",
          // subtitles
          "srt",
          "vtt",
          // data and configuration
          "csv",
          "tsv",
          "json",
          "xml",
          "yaml",
          "yml",
          "toml",
          "ini",
          "properties",
          "env",
          // web
          "html",
          "htm",
          "css",
          "js",
          "mjs",
          "ts",
          "jsx",
          "tsx",
          // source code
          "java",
          "kt",
          "py",
          "rb",
          "go",
          "rs",
          "c",
          "h",
          "cpp",
          "hpp",
          "cs",
          "php",
          "sql",
          "sh",
          "bash",
          "tex");

  public TextFilePolicy() {}

  /** Whether the file can be opened in the text editor. */
  public boolean isText(MediaFileSnapshot f) {
    Objects.requireNonNull(f, "f");
    return f.sizeBytes() <= MAX_BYTES
        && extension(f.path()).filter(EXTENSIONS::contains).isPresent();
  }

  /**
   * The text of {@code bytes}, read as UTF-8.
   *
   * @return empty if the bytes are not valid UTF-8 or are more than {@value #MAX_BYTES}
   */
  public Optional<String> decode(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    if (bytes.length > MAX_BYTES) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes))
              .toString());
    } catch (CharacterCodingException e) {
      return Optional.empty();
    }
  }

  /** The extension of the file name, in lower case; none for {@code README} or {@code .env}. */
  static Optional<String> extension(Path path) {
    Path name = Objects.requireNonNull(path, "path").getFileName();
    if (name == null) {
      return Optional.empty();
    }
    String file = name.toString();
    int dot = file.lastIndexOf('.');
    if (dot <= 0 || dot == file.length() - 1) {
      return Optional.empty();
    }
    return Optional.of(file.substring(dot + 1).toLowerCase(Locale.ROOT));
  }
}
