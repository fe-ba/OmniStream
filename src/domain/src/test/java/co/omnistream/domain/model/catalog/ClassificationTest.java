package co.omnistream.domain.model.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClassificationTest {

  private final Tag video = new Tag("video", TagSource.AUTOMATIC);
  private final Tag year = new Tag("2026", TagSource.AUTOMATIC);
  private final ItemId trips = new ItemId(UUID.randomUUID());

  @Test
  void theEmptyClassificationDecidesNothing() {
    assertThat(Classification.empty().isEmpty()).isTrue();
    assertThat(Classification.empty().tags()).isEmpty();
    assertThat(Classification.empty().collections()).isEmpty();
  }

  @Test
  void aClassificationWithATagIsNotEmpty() {
    Classification classification = new Classification(Set.of(video), Set.of());

    assertThat(classification.isEmpty()).isFalse();
  }

  @Test
  void combineJoinsTagsAndCollections() {
    Classification byType = new Classification(Set.of(video), Set.of());
    Classification byDate = new Classification(Set.of(year, video), Set.of(trips));

    Classification combined = byType.combine(byDate);

    assertThat(combined.tags()).containsExactlyInAnyOrder(video, year);
    assertThat(combined.collections()).containsExactly(trips);
  }

  @Test
  void combiningWithTheEmptyClassificationChangesNothing() {
    Classification byType = new Classification(Set.of(video), Set.of(trips));

    assertThat(byType.combine(Classification.empty())).isEqualTo(byType);
  }

  @Test
  void rejectsManualTags() {
    Set<Tag> tags = Set.of(new Tag("favoritos", TagSource.MANUAL));

    assertThatThrownBy(() -> new Classification(tags, Set.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("favoritos");
  }

  @Test
  void isNotChangedThroughTheSetsItWasBuiltWith() {
    Set<Tag> tags = new HashSet<>(Set.of(video));
    Classification classification = new Classification(tags, Set.of());

    tags.add(year);

    assertThat(classification.tags()).containsExactly(video);
  }
}
