package co.omnistream.domain.service.ingestion;

import co.omnistream.domain.model.catalog.IngestionResult;
import java.util.Objects;

/** Handler of the ingestion chain (Chain of Responsibility, Template Method). */
public abstract class IngestionStep {
  private IngestionStep next;

  /**
   * Puts {@code next} after this step and returns it, so a chain reads in order: {@code
   * extraction.chain(duplicates).chain(classification).chain(indexing)}. The composition root
   * builds the chain this way at start-up.
   */
  public IngestionStep chain(IngestionStep next) {
    this.next = Objects.requireNonNull(next, "next");
    return next;
  }

  public IngestionResult execute(IngestionContext ctx) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  protected abstract boolean process(IngestionContext ctx);
}
