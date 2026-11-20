package co.omnistream.domain.service.ingestion;

/** Concrete handler of the ingestion chain (Chain of Responsibility). */
public final class IndexingStep extends IngestionStep {
  @Override
  protected boolean process(IngestionContext ctx) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
