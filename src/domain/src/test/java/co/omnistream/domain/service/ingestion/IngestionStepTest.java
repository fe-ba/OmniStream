package co.omnistream.domain.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IngestionStepTest {

  private static IngestionStep step() {
    return new IngestionStep() {
      @Override
      protected boolean process(IngestionContext ctx) {
        return true;
      }
    };
  }

  @Test
  void chainReturnsTheNextStepSoAChainReadsInOrder() {
    IngestionStep first = step();
    IngestionStep second = step();
    IngestionStep third = step();

    assertThat(first.chain(second)).isSameAs(second);
    assertThat(second.chain(third)).isSameAs(third);
  }

  @Test
  void aChainHasNoGaps() {
    assertThatThrownBy(() -> step().chain(null)).isInstanceOf(NullPointerException.class);
  }
}
