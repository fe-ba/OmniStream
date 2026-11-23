package co.omnistream.infrastructure.media;

import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.model.catalog.MetadataSource;
import co.omnistream.domain.port.out.IMetadataExtractor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Secondary adapter that reads the metadata of a file with FFprobe, for audio and video, and with
 * ExifTool, for images and documents (ADR 0016).
 *
 * <p>It only reads: the entries it answers are {@link MetadataSource#EXTRACTED}, and whoever saves
 * them never replaces a value a user wrote. A tool that is not installed stops the indexing of that
 * file, because the duration and the codec are needed to catalogue it; an image or a document
 * without ExifTool is still catalogued, with no metadata.
 */
public final class MetadataAdapter implements IMetadataExtractor {

  private static final System.Logger LOG = System.getLogger(MetadataAdapter.class.getName());
  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** Metadata values FFprobe and ExifTool share; they are stored under these names. */
  private static final Map<String, String> KEYS =
      Map.ofEntries(
          Map.entry("duration", "duration"),
          Map.entry("bit_rate", "bitrate"),
          Map.entry("bitrate", "bitrate"),
          Map.entry("format_name", "format"),
          Map.entry("format", "format"),
          Map.entry("codec_name", "codec"),
          Map.entry("codec", "codec"),
          Map.entry("width", "width"),
          Map.entry("height", "height"),
          Map.entry("title", "title"),
          Map.entry("artist", "artist"),
          Map.entry("album", "album"),
          Map.entry("date", "date"),
          Map.entry("creationdate", "date"),
          Map.entry("datetimeoriginal", "date"));

  private final Path ffprobe;
  private final Path exifTool;

  /**
   * @param ffprobe the FFprobe executable; a bare name is looked up in the {@code PATH}
   * @param exifTool the ExifTool executable; a bare name is looked up in the {@code PATH}
   */
  public MetadataAdapter(Path ffprobe, Path exifTool) {
    this.ffprobe = Objects.requireNonNull(ffprobe, "ffprobe");
    this.exifTool = Objects.requireNonNull(exifTool, "exifTool");
  }

  @Override
  public boolean supports(MediaType type) {
    return Objects.requireNonNull(type, "type") == MediaType.AUDIO
        || type == MediaType.VIDEO
        || type == MediaType.IMAGE
        || type == MediaType.DOCUMENT;
  }

  /**
   * The metadata of {@code path}: what FFprobe says about its streams and container, or what
   * ExifTool says about an image or a document.
   */
  @Override
  public List<MetadataEntry> extract(Path path) {
    Objects.requireNonNull(path, "path");
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    if (isAv(name)) {
      return ffprobe(path);
    }
    return exifTool(path);
  }

  private static boolean isAv(String name) {
    return switch (MediaTools.extension(name)) {
      case "mp4", "m4v", "mkv", "avi", "mov", "webm", "flv", "wmv", "mpg", "mpeg" -> true;
      case "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "aiff" -> true;
      default -> false;
    };
  }

  /** Reads a media file with FFprobe. */
  private List<MetadataEntry> ffprobe(Path path) {
    List<String> command =
        List.of(
            ffprobe.toString(),
            "-v",
            "quiet",
            "-print_format",
            "json",
            "-show_format",
            "-show_streams",
            path.toString());
    return parse(MediaTools.run(command), "format", "streams");
  }

  /** Reads an image or a document with ExifTool. */
  private List<MetadataEntry> exifTool(Path path) {
    try {
      String output = MediaTools.run(List.of(exifTool.toString(), "-json", "-n", path.toString()));
      JsonNode root = JSON.readTree(output);
      if (!root.isArray() || root.isEmpty()) {
        return List.of();
      }
      return extracted(root.get(0));
    } catch (RuntimeException e) {
      // Metadata of images and documents is optional: the file is catalogued without it
      LOG.log(
          System.Logger.Level.WARNING,
          "Could not read the metadata of " + path + " with ExifTool",
          e);
      return List.of();
    }
  }

  /** Turns the FFprobe answer into entries. */
  private static List<MetadataEntry> parse(String output, String... sections) {
    JsonNode root;
    try {
      root = JSON.readTree(output);
    } catch (RuntimeException e) {
      throw new MediaToolException("FFprobe answered something that is not JSON", e);
    }
    List<MetadataEntry> entries = new ArrayList<>();
    for (String section : sections) {
      JsonNode node = root.path(section);
      if ("streams".equals(section) && node.isArray()) {
        for (JsonNode stream : node) {
          add(entries, stream);
        }
      } else {
        add(entries, node);
      }
    }
    return List.copyOf(entries);
  }

  /** The fields of one object that are worth keeping. */
  private static List<MetadataEntry> extracted(JsonNode node) {
    List<MetadataEntry> entries = new ArrayList<>();
    add(entries, node);
    return List.copyOf(entries);
  }

  private static void add(List<MetadataEntry> entries, JsonNode node) {
    if (node == null || node.isMissingNode() || !node.isObject()) {
      return;
    }
    node.properties()
        .forEach(field -> {
          String key = KEYS.get(field.getKey().toLowerCase(Locale.ROOT));
          if (key == null) {
            return;
          }
          String value = field.getValue().asText().strip();
          if (!value.isEmpty()) {
            entries.add(new MetadataEntry(key, value, MetadataSource.EXTRACTED));
          }
        });
  }
}
