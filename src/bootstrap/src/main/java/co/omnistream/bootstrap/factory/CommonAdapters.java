package co.omnistream.bootstrap.factory;

import co.omnistream.bootstrap.config.AppConfiguration;
import co.omnistream.domain.port.out.IEventBus;
import co.omnistream.domain.port.out.IFileStorage;
import co.omnistream.domain.port.out.IMetadataExtractor;
import co.omnistream.domain.port.out.ITokenRepository;
import co.omnistream.domain.port.out.ITranscoder;
import co.omnistream.infrastructure.event.EventBusAdapter;
import co.omnistream.infrastructure.media.MetadataAdapter;
import co.omnistream.infrastructure.media.TranscodingAdapter;
import co.omnistream.infrastructure.storage.LocalFileStorageAdapter;
import co.omnistream.infrastructure.token.InMemoryTokenAdapter;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;

/**
 * Adapters that both profiles use as they are: the local files, the metadata tools, the transcoder,
 * the in-memory tokens and the event bus (ADR 0009).
 *
 * <p>It exists so the two concrete factories do not repeat them; the persistence adapter and the
 * identity adapter, which do change, stay in each factory.
 */
final class CommonAdapters {

  private final IFileStorage storage;
  private final ITokenRepository tokens;
  private final IMetadataExtractor metadata;
  private final ITranscoder transcoder;
  private final IEventBus eventBus;

  CommonAdapters(AppConfiguration configuration, Clock clock) {
    Objects.requireNonNull(configuration, "configuration");
    this.storage = new LocalFileStorageAdapter(configuration.libraryPath());
    this.tokens = new InMemoryTokenAdapter(clock);
    this.metadata =
        new MetadataAdapter(
            ffprobeNextTo(configuration.ffmpegPath()), configuration.exifToolPath());
    this.transcoder = new TranscodingAdapter(configuration.ffmpegPath(), temporaryFolder());
    this.eventBus = new EventBusAdapter();
  }

  IFileStorage storage() {
    return storage;
  }

  ITokenRepository tokens() {
    return tokens;
  }

  IMetadataExtractor metadata() {
    return metadata;
  }

  ITranscoder transcoder() {
    return transcoder;
  }

  IEventBus eventBus() {
    return eventBus;
  }

  /** FFprobe usually sits next to FFmpeg; a bare name is looked up in the {@code PATH}. */
  private static Path ffprobeNextTo(Path ffmpeg) {
    Path name = ffmpeg.getFileName();
    if (name == null || !name.toString().startsWith("ffmpeg")) {
      return Path.of("ffprobe");
    }
    Path parent = ffmpeg.getParent();
    String executable = name.toString().contains(".") ? "ffprobe.exe" : "ffprobe";
    return parent == null ? Path.of(executable) : parent.resolve(executable);
  }

  private static Path temporaryFolder() {
    return Path.of(System.getProperty("java.io.tmpdir"), "omnistream", "transcode");
  }
}
