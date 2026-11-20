package co.omnistream.domain.service.ingestion;

import co.omnistream.domain.model.catalog.IngestionResult;

/** Handler of the ingestion chain (Chain of Responsibility, Template Method). */
public abstract class IngestionStep {
  private IngestionStep next;

  public IngestionStep chain(IngestionStep next) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public IngestionResult execute(IngestionContext ctx) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  protected abstract boolean process(IngestionContext ctx);
}
