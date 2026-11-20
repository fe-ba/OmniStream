package co.omnistream.domain.model.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngestionResultTest {

  private final ItemId fileId = new ItemId(UUID.randomUUID());

  @Test
  void aNewFileIsIndexed() {
    IngestionResult result = new IngestionResult(fileId, false, Optional.empty());

    assertThat(result.wasIndexed()).isTrue();
  }

  @Test
  void aRejectedDuplicateIsNotIndexed() {
    IngestionResult result = new IngestionResult(fileId, true, Optional.of(DuplicateAction.REJECT));

    assertThat(result.wasIndexed()).isFalse();
  }

  @Test
  void aReplacingDuplicateIsIndexed() {
    IngestionResult result =
        new IngestionResult(fileId, true, Optional.of(DuplicateAction.REPLACE));

    assertThat(result.wasIndexed()).isTrue();
  }

  @Test
  void aDuplicateKeptBesideTheOriginalIsIndexed() {
    IngestionResult result =
        new IngestionResult(fileId, true, Optional.of(DuplicateAction.KEEP_BOTH));

    assertThat(result.wasIndexed()).isTrue();
  }

  @Test
  void aDuplicateNeedsAnAction() {
    assertThatThrownBy(() -> new IngestionResult(fileId, true, Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aNewFileCannotHaveAnAction() {
    assertThatThrownBy(
            () -> new IngestionResult(fileId, false, Optional.of(DuplicateAction.REPLACE)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
