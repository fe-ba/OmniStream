package co.omnistream.domain.service.ingestion;

import co.omnistream.domain.policy.DeduplicationPolicy;

/** Concrete handler of the ingestion chain (Chain of Responsibility). */
public final class DuplicatesStep extends IngestionStep {
  private final DeduplicationPolicy policy;

  public DuplicatesStep(DeduplicationPolicy policy) {
    this.policy = policy;
  }

  @Override
  protected boolean process(IngestionContext ctx) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
