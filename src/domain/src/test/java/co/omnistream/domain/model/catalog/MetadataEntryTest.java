package co.omnistream.domain.model.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MetadataEntryTest {

  @Test
  void anExtractedEntryIsNotManual() {
    MetadataEntry entry = new MetadataEntry("camera", "Canon EOS R6", MetadataSource.EXTRACTED);

    assertThat(entry.isManual()).isFalse();
  }

  @Test
  void aManualEntryIsManual() {
    MetadataEntry entry = new MetadataEntry("location", "Bogotá", MetadataSource.MANUAL);

    assertThat(entry.isManual()).isTrue();
  }

  @Test
  void editingAValueMakesItManual() {
    MetadataEntry extracted = new MetadataEntry("title", "IMG_0001", MetadataSource.EXTRACTED);

    MetadataEntry edited = extracted.withValue("Beach at sunset");

    assertThat(edited.key()).isEqualTo("title");
    assertThat(edited.value()).isEqualTo("Beach at sunset");
    assertThat(edited.isManual()).isTrue();
    assertThat(extracted.value()).isEqualTo("IMG_0001");
  }

  @Test
  void stripsTheKey() {
    MetadataEntry entry = new MetadataEntry("  codec ", "h264", MetadataSource.EXTRACTED);

    assertThat(entry.key()).isEqualTo("codec");
  }

  @Test
  void rejectsABlankKey() {
    assertThatThrownBy(() -> new MetadataEntry(" ", "value", MetadataSource.MANUAL))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAMissingValue() {
    assertThatThrownBy(() -> new MetadataEntry("codec", null, MetadataSource.EXTRACTED))
        .isInstanceOf(NullPointerException.class);
  }
}
