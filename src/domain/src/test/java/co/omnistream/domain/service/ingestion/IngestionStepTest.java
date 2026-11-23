package co.omnistream.domain.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.catalog.IngestionResult;
import co.omnistream.domain.model.catalog.ItemId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngestionStepTest {

  @Test
  void runsTheStepsInOrderAndStopsWhenOneSaysSo() {
    List<String> order = new ArrayList<>();
    IngestionResult expected = result();
    IngestionStep chain =
        step("first", order, true, null)
            .chain(step("second", order, false, expected))
            .chain(step("third", order, true, result()));

    IngestionResult result = chain.execute(new IngestionContext());

    assertThat(order).containsExactly("first", "second");
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void theLastStepLeavesItsResultInTheContext() {
    IngestionResult expected = result();
    IngestionContext context = new IngestionContext();
    IngestionStep chain = step("only", new ArrayList<>(), true, expected);

    assertThat(chain.execute(context)).isEqualTo(expected);
  }

  private static IngestionStep step(
      String name, List<String> order, boolean keepGoing, IngestionResult result) {
    return new IngestionStep() {
      @Override
      protected boolean process(IngestionContext ctx) {
        order.add(name);
        if (result != null) {
          ctx.result = result;
        }
        return keepGoing;
      }
    };
  }

  private static IngestionResult result() {
    return new IngestionResult(
        new ItemId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
        false,
        Optional.empty());
  }
}
