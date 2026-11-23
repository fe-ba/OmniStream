package co.omnistream.domain.port.out.memory;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.model.catalog.MetadataSource;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryMetadataExtractorTest {

  @Test
  void returnsTheConfiguredEntriesForTheSupportedTypes() {
    MetadataEntry codec = new MetadataEntry("codec", "h264", MetadataSource.EXTRACTED);
    InMemoryMetadataExtractor extractor =
        new InMemoryMetadataExtractor(List.of(codec), MediaType.VIDEO);

    assertThat(extractor.supports(MediaType.VIDEO)).isTrue();
    assertThat(extractor.supports(MediaType.AUDIO)).isFalse();
    assertThat(extractor.extract(Path.of("video.mp4"))).containsExactly(codec);
  }
}
