package co.omnistream.domain.model.catalog;

import static co.omnistream.domain.model.catalog.CatalogFixtures.NOW;
import static co.omnistream.domain.model.catalog.CatalogFixtures.fingerprint;
import static co.omnistream.domain.model.catalog.CatalogFixtures.video;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MediaFileTest {

  private final MediaFile file = video("Trip", "/library/trip.mp4", 'a');

  @Test
  void aNewFileIsPending() {
    MediaFileSnapshot snapshot = file.toSnapshot();

    assertThat(snapshot.status()).isEqualTo(IndexingStatus.PENDING);
    assertThat(snapshot.ingestedAt()).isEqualTo(NOW);
    assertThat(snapshot.metadata()).isEmpty();
    assertThat(snapshot.tags()).isEmpty();
  }

  @Test
  void rejectsANegativeSize() {
    assertThatThrownBy(() ->
            MediaFile.create(
                "x", Path.of("/x"), fingerprint('a'), MediaType.AUDIO, -1, Duration.ZERO, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsANegativeDuration() {
    assertThatThrownBy(() ->
            MediaFile.create(
                "x",
                Path.of("/x"),
                fingerprint('a'),
                MediaType.AUDIO,
                1,
                Duration.ofSeconds(-1),
                NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsABlankName() {
    assertThatThrownBy(() -> video(" ", "/x", 'a')).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anotherFileWithTheSameContentIsADuplicate() {
    MediaFile copy = video("Trip copy", "/backup/trip.mp4", 'a');

    assertThat(copy.isDuplicateOf(file)).isTrue();
  }

  @Test
  void aFileIsNotADuplicateOfItself() {
    assertThat(file.isDuplicateOf(file)).isFalse();
  }

  @Test
  void aFileWithOtherContentIsNotADuplicate() {
    assertThat(video("Other", "/library/other.mp4", 'b').isDuplicateOf(file)).isFalse();
  }

  @Test
  void addMetadataReplacesTheEntryWithTheSameKey() {
    file.addMetadata(new MetadataEntry("codec", "h264", MetadataSource.EXTRACTED));

    file.addMetadata(new MetadataEntry("codec", "hevc", MetadataSource.EXTRACTED));

    assertThat(file.metadataEntry("codec").map(MetadataEntry::value)).contains("hevc");
    assertThat(file.toSnapshot().metadata()).hasSize(1);
  }

  @Test
  void anExtractedValueDoesNotOverwriteAManualOne() {
    file.addMetadata(new MetadataEntry("title", "Beach", MetadataSource.MANUAL));

    file.addMetadata(new MetadataEntry("title", "IMG_0001", MetadataSource.EXTRACTED));

    assertThat(file.metadataEntry("title").map(MetadataEntry::value)).contains("Beach");
  }

  @Test
  void aManualValueOverwritesAnExtractedOne() {
    file.addMetadata(new MetadataEntry("title", "IMG_0001", MetadataSource.EXTRACTED));

    file.addMetadata(new MetadataEntry("title", "Beach", MetadataSource.MANUAL));

    assertThat(file.metadataEntry("title").map(MetadataEntry::value)).contains("Beach");
  }

  @Test
  void findsMetadataIgnoringSurroundingSpaces() {
    file.addMetadata(new MetadataEntry("codec", "h264", MetadataSource.EXTRACTED));

    assertThat(file.metadataEntry(" codec ")).isPresent();
    assertThat(file.metadataEntry("camera")).isEmpty();
  }

  @Test
  void aTagWithTheSameNormalizedNameIsNotAddedTwice() {
    file.tag(new Tag("Música", TagSource.MANUAL));

    file.tag(new Tag("musica", TagSource.MANUAL));

    assertThat(file.toSnapshot().tags()).containsExactly(new Tag("Música", TagSource.MANUAL));
  }

  @Test
  void aManualTagTakesThePlaceOfAnAutomaticOne() {
    file.tag(new Tag("video", TagSource.AUTOMATIC));

    file.tag(new Tag("Video", TagSource.MANUAL));

    assertThat(file.toSnapshot().tags()).containsExactly(new Tag("Video", TagSource.MANUAL));
  }

  @Test
  void anAutomaticTagNeverReplacesAManualOne() {
    file.tag(new Tag("Video", TagSource.MANUAL));

    file.tag(new Tag("video", TagSource.AUTOMATIC));

    assertThat(file.toSnapshot().tags()).containsExactly(new Tag("Video", TagSource.MANUAL));
  }

  @Test
  void replaceAutomaticTagsKeepsTheManualOnes() {
    file.tag(new Tag("favorites", TagSource.MANUAL));
    file.tag(new Tag("2025", TagSource.AUTOMATIC));

    file.replaceAutomaticTags(Set.of(new Tag("2026", TagSource.AUTOMATIC)));

    assertThat(file.toSnapshot().tags())
        .containsExactlyInAnyOrder(
            new Tag("favorites", TagSource.MANUAL), new Tag("2026", TagSource.AUTOMATIC));
  }

  @Test
  void replaceAutomaticTagsDoesNotDuplicateAManualTag() {
    file.tag(new Tag("Video", TagSource.MANUAL));

    file.replaceAutomaticTags(Set.of(new Tag("video", TagSource.AUTOMATIC)));

    assertThat(file.toSnapshot().tags()).containsExactly(new Tag("Video", TagSource.MANUAL));
  }

  @Test
  void replaceAutomaticTagsOnlyAcceptsAutomaticTags() {
    Set<Tag> manual = Set.of(new Tag("favorites", TagSource.MANUAL));

    assertThatThrownBy(() -> file.replaceAutomaticTags(manual))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void marksTheIndexingOutcome() {
    file.markIndexed();
    assertThat(file.toSnapshot().status()).isEqualTo(IndexingStatus.INDEXED);

    file.markFailed("ffprobe could not read the file");
    assertThat(file.toSnapshot().status()).isEqualTo(IndexingStatus.ERROR);
  }

  @Test
  void asALeafItCountsOnlyItself() {
    assertThat(file.totalSize()).isEqualTo(1_000L);
    assertThat(file.countFiles()).isEqualTo(1);
    assertThat(file.contains(video("Other", "/o.mp4", 'b'))).isFalse();
  }

  @Test
  void theSnapshotCannotChangeTheFile() {
    MediaFileSnapshot snapshot = file.toSnapshot();

    assertThatThrownBy(() -> snapshot.tags().add(new Tag("x", TagSource.MANUAL)))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
