package co.omnistream.domain.service.ingestion;

import co.omnistream.domain.model.catalog.IngestionResult;
import java.util.Objects;

/**
 * Handler of the ingestion chain (Chain of Responsibility, Template Method).
 *
 * <p>Subclasses implement {@link #process(IngestionContext)}; {@link #execute(IngestionContext)}
 * forwards to the next handler only when {@code process} returns {@code true}.
 */
public abstract class IngestionStep {
  private IngestionStep next;

  /**
   * Appends {@code next} after this step and returns {@code next} so steps can be chained fluently.
   */
  public IngestionStep chain(IngestionStep next) {
    Objects.requireNonNull(next, "next");
    if (next == this) {
      throw new IllegalArgumentException("An ingestion step cannot chain to itself");
    }
    this.next = next;
    return next;
  }

  /**
   * Template Method: runs {@link #process(IngestionContext)} and moves to the next step while each
   * step returns {@code true}.
   */
  public IngestionResult execute(IngestionContext ctx) {
    Objects.requireNonNull(ctx, "ctx");
    if (process(ctx) && next != null) {
      return next.execute(ctx);
    }
    return ctx.result;
  }

  protected abstract boolean process(IngestionContext ctx);
}
