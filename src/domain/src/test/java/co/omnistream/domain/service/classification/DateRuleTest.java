package co.omnistream.domain.service.classification;

import static co.omnistream.domain.model.catalog.CatalogFixtures.NOW;
import static co.omnistream.domain.model.catalog.CatalogFixtures.video;
import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.catalog.Classification;
import co.omnistream.domain.model.catalog.MediaFile;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.model.catalog.MetadataSource;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.model.catalog.TagSource;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import org.junit.jupiter.api.Test;

class DateRuleTest {

  private final DateRule rule = new DateRule();

  @Test
  void doesNotApplyWhenCaptureDateIsMissingOrUnparseable() {
    MediaFile withoutDate = video("clip", "/library/clip.mp4", 'a');
    MediaFile invalidDate = video("clip2", "/library/clip2.mp4", 'b');
    invalidDate.addMetadata(
        new MetadataEntry("captureDate", "not-a-date", MetadataSource.EXTRACTED));

    assertThat(rule.appliesTo(withoutDate.toSnapshot())).isFalse();
    assertThat(rule.classify(withoutDate.toSnapshot())).isEqualTo(Classification.empty());
    assertThat(rule.appliesTo(invalidDate.toSnapshot())).isFalse();
    assertThat(rule.classify(invalidDate.toSnapshot())).isEqualTo(Classification.empty());
  }

  @Test
  void parsesIsoInstantLocalDateLocalDateTimeAndExifFormats() {
    String[] samples = {
      "2024-08-19T14:30:00Z",
      "2024-08-19",
      "2024-08-19T14:30:00",
      "2024:08:19 14:30:00",
      "2024:08:19"
    };

    for (String sample : samples) {
      MediaFile file = video("clip", "/library/clip.mp4", 'a');
      file.addMetadata(new MetadataEntry("captureDate", sample, MetadataSource.EXTRACTED));
      MediaFileSnapshot snapshot = file.toSnapshot();

      assertThat(rule.appliesTo(snapshot)).isTrue();
      assertThat(rule.classify(snapshot).tags())
          .containsExactlyInAnyOrder(
              new Tag("2024", TagSource.AUTOMATIC), new Tag("2024-08", TagSource.AUTOMATIC));
    }
  }

  @Test
  void prioritizesCaptureDateOverCreationTimeAndDate() {
    MediaFile file = video("photo", "/library/photo.jpg", 'a');
    file.addMetadata(new MetadataEntry("date", "2020-01-05", MetadataSource.EXTRACTED));
    file.addMetadata(
        new MetadataEntry("creation_time", "2022-05-10T10:00:00Z", MetadataSource.EXTRACTED));
    file.addMetadata(new MetadataEntry("captureDate", "2025-11-03", MetadataSource.EXTRACTED));

    Classification result = rule.classify(file.toSnapshot());

    assertThat(result.tags())
        .containsExactlyInAnyOrder(
            new Tag("2025", TagSource.AUTOMATIC), new Tag("2025-11", TagSource.AUTOMATIC));
    assertThat(result.collections()).isEmpty();
    assertThat(file.toSnapshot().ingestedAt()).isEqualTo(NOW);
  }
}
