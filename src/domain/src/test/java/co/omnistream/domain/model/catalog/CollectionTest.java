package co.omnistream.domain.model.catalog;

import static co.omnistream.domain.model.catalog.CatalogFixtures.video;
import static co.omnistream.domain.model.catalog.CatalogFixtures.videoOfSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.exception.CyclicCollection;
import org.junit.jupiter.api.Test;

class CollectionTest {

  private final Collection trips = Collection.create("Trips");
  private final Collection beach = Collection.create("Beach");

  @Test
  void containsItemsAtAnyDepth() {
    MediaFile sunset = video("Sunset", "/s.mp4", 'a');
    beach.add(sunset);

    trips.add(beach);

    assertThat(trips.contains(beach)).isTrue();
    assertThat(trips.contains(sunset)).isTrue();
    assertThat(beach.contains(trips)).isFalse();
  }

  @Test
  void cannotContainItself() {
    assertThatThrownBy(() -> trips.add(trips)).isInstanceOf(CyclicCollection.class);
  }

  @Test
  void cannotContainOneOfItsAncestors() {
    Collection summer = Collection.create("Summer");
    trips.add(beach);
    beach.add(summer);

    assertThatThrownBy(() -> summer.add(trips)).isInstanceOf(CyclicCollection.class);
  }

  @Test
  void addingTheSameItemTwiceKeepsOneCopy() {
    MediaFile sunset = video("Sunset", "/s.mp4", 'a');

    beach.add(sunset);
    beach.add(sunset);

    assertThat(beach.toSnapshot().items()).hasSize(1);
  }

  @Test
  void removesADirectMember() {
    MediaFile sunset = video("Sunset", "/s.mp4", 'a');
    beach.add(sunset);

    beach.remove(sunset);

    assertThat(beach.contains(sunset)).isFalse();
  }

  @Test
  void totalsTheSizeOfItsFilesRecursively() {
    beach.add(videoOfSize("a", 'a', 100));
    beach.add(videoOfSize("b", 'b', 200));
    trips.add(beach);
    trips.add(videoOfSize("c", 'c', 50));

    assertThat(trips.totalSize()).isEqualTo(350L);
    assertThat(trips.countFiles()).isEqualTo(3);
  }

  @Test
  void aFileInTwoSubCollectionsCountsOnce() {
    MediaFile shared = videoOfSize("shared", 'a', 100);
    Collection mountains = Collection.create("Mountains");
    beach.add(shared);
    mountains.add(shared);
    trips.add(beach);
    trips.add(mountains);

    assertThat(trips.totalSize()).isEqualTo(100L);
    assertThat(trips.countFiles()).isEqualTo(1);
  }

  @Test
  void anEmptyCollectionHasNothing() {
    assertThat(trips.totalSize()).isEqualTo(0L);
    assertThat(trips.countFiles()).isEqualTo(0);
    assertThat(trips.depth()).isEqualTo(1);
  }

  @Test
  void depthCountsTheLevelsOfSubCollections() {
    Collection summer = Collection.create("Summer");
    beach.add(summer);
    trips.add(beach);
    trips.add(video("x", "/x.mp4", 'a'));

    assertThat(trips.depth()).isEqualTo(3);
  }

  @Test
  void canBeRenamedButNotToABlankName() {
    trips.rename("  Travels ");

    assertThat(trips.toSnapshot().name()).isEqualTo("Travels");
    assertThatThrownBy(() -> trips.rename(" ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theSnapshotListsItsItemsInOrder() {
    MediaFile first = video("1", "/1.mp4", 'a');
    MediaFile second = video("2", "/2.mp4", 'b');
    trips.add(first);
    trips.add(beach);
    trips.add(second);

    assertThat(trips.toSnapshot().items())
        .containsExactly(
            first.toSnapshot().id(), beach.toSnapshot().id(), second.toSnapshot().id());
  }
}
