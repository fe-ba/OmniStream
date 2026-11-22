package co.omnistream.domain.model.catalog;

import static co.omnistream.domain.model.catalog.CatalogFixtures.NOW;
import static co.omnistream.domain.model.catalog.CatalogFixtures.fingerprint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.event.CatalogEvent;
import co.omnistream.domain.event.FileModified;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContentReplacementTest {

  private final CatalogIndex catalog =
      CatalogIndex.fromSnapshot(
          new CatalogSnapshot(
              new CatalogId(UUID.randomUUID()), "library", 0, List.of(), List.of()));
  private final MediaFile notes =
      MediaFile.create(
          "notes.md",
          Path.of("/library/notes.md"),
          fingerprint('a'),
          MediaType.DOCUMENT,
          120,
          Duration.ZERO,
          NOW);

  private MediaFileSnapshot stored() {
    return catalog.toSnapshot().files().get(0);
  }

  @Test
  void newContentChangesTheFingerprintAndSizeAndIsAModification() {
    catalog.ingest(notes, NOW);
    catalog.pullEvents();

    catalog.replaceContent(notes.id, fingerprint('b'), 200, NOW.plusSeconds(5));

    assertThat(stored().fingerprint()).isEqualTo(fingerprint('b'));
    assertThat(stored().sizeBytes()).isEqualTo(200);
    List<CatalogEvent> events = catalog.pullEvents();
    assertThat(events).hasSize(1);
    assertThat(events.get(0)).isInstanceOf(FileModified.class);
    assertThat(events.get(0).item()).isEqualTo(notes.id);
    assertThat(events.get(0).occurredAt()).isEqualTo(NOW.plusSeconds(5));
  }

  @Test
  void theSameContentAgainChangesNothing() {
    catalog.ingest(notes, NOW);
    catalog.pullEvents();

    catalog.replaceContent(notes.id, fingerprint('a'), 120, NOW);

    assertThat(catalog.pullEvents()).isEmpty();
  }

  @Test
  void nameMetadataAndTagsStay() {
    notes.tag(new Tag("work", TagSource.MANUAL));
    catalog.ingest(notes, NOW);

    catalog.replaceContent(notes.id, fingerprint('b'), 200, NOW);

    assertThat(stored().name()).isEqualTo("notes.md");
    assertThat(stored().tags()).containsExactly(new Tag("work", TagSource.MANUAL));
    assertThat(stored().status()).isEqualTo(IndexingStatus.INDEXED);
  }

  @Test
  void anUnknownFileIsNotFound() {
    assertThatThrownBy(
            () -> catalog.replaceContent(new ItemId(UUID.randomUUID()), fingerprint('b'), 1, NOW))
        .isInstanceOf(ResourceNotFound.class);
  }

  @Test
  void aNegativeSizeIsRejected() {
    catalog.ingest(notes, NOW);

    assertThatThrownBy(() -> catalog.replaceContent(notes.id, fingerprint('b'), -1, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
