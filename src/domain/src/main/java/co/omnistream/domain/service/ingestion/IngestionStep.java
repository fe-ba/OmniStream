package co.omnistream.domain.service.ingestion;

import co.omnistream.domain.model.catalog.IngestionResult;
import java.util.Objects;

/** Handler of the ingestion chain (Chain of Responsibility, Template Method). */
public abstract class IngestionStep {
  private IngestionStep next;

  /**
   * Appends {@code next} at the end of the chain and returns the first step, so the chain can be
   * built in one expression: {@code new ExtractionStep(...).chain(new
   * DuplicatesStep(...)).chain(new IndexingStep())}.
   *
   * @return this first step, to keep chaining
   */
  public IngestionStep chain(IngestionStep next) {
    Objects.requireNonNull(next, "next");
    IngestionStep last = this;
    while (last.next != null) {
      last = last.next;
    }
    last.next = next;
    return this;
  }

  /**
   * Runs this step's {@link #process} and, if it says to go on, runs the next one. The result of
   * the last step that ran stays in the context.
   */
  public IngestionResult execute(IngestionContext ctx) {
    Objects.requireNonNull(ctx, "ctx");
    boolean keepGoing = process(ctx);
    if (keepGoing && next != null) {
      return next.execute(ctx);
    }
    return ctx.result;
  }

  protected abstract boolean process(IngestionContext ctx);
}
