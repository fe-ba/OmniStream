package co.omnistream.domain.policy;

import static co.omnistream.domain.model.catalog.CatalogFixtures.NOW;
import static co.omnistream.domain.model.catalog.CatalogFixtures.video;
import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.catalog.CatalogId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.DuplicateAction;
import co.omnistream.domain.model.catalog.MediaFile;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeduplicationPolicyTest {

  private final DeduplicationPolicy policy = new DeduplicationPolicy();
  private final MediaFile existing = video("Trip", "/library/trip.mp4", 'a');

  @Test
  void theSameFileSeenAgainIsRejected() {
    MediaFile again = video("Trip", "/library/./trip.mp4", 'a');

    assertThat(policy.resolve(again, existing)).isEqualTo(DuplicateAction.REJECT);
  }

  @Test
  void aCopyInAnotherPlaceIsKept() {
    MediaFile copy = video("Trip", "/backup/trip.mp4", 'a');

    assertThat(policy.resolve(copy, existing)).isEqualTo(DuplicateAction.KEEP_BOTH);
  }

  @Test
  void aFileThatFailedToIndexIsReplaced() {
    existing.markFailed("unreadable");
    MediaFile again = video("Trip", "/library/trip.mp4", 'a');

    assertThat(policy.resolve(again, existing)).isEqualTo(DuplicateAction.REPLACE);
  }

  @Test
  void detectsADuplicateInTheCatalog() {
    CatalogIndex catalog = emptyCatalog();
    catalog.ingest(existing, NOW);

    assertThat(policy.isDuplicate(video("Copy", "/backup/trip.mp4", 'a'), catalog)).isTrue();
    assertThat(policy.isDuplicate(video("Other", "/library/other.mp4", 'b'), catalog)).isFalse();
  }

  private static CatalogIndex emptyCatalog() {
    return CatalogIndex.fromSnapshot(
        new CatalogSnapshot(new CatalogId(UUID.randomUUID()), "Library", 0, List.of(), List.of()));
  }
}
