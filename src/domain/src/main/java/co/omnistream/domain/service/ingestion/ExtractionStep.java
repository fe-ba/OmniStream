package co.omnistream.domain.service.ingestion;

import co.omnistream.domain.port.out.IFileStorage;
import co.omnistream.domain.port.out.IMetadataExtractor;
import java.time.Clock;

/** Concrete handler of the ingestion chain (Chain of Responsibility). */
public final class ExtractionStep extends IngestionStep {
  private final IFileStorage storage;
  private final IMetadataExtractor extractor;
  private final Clock clock;

  public ExtractionStep(IFileStorage storage, IMetadataExtractor extractor, Clock clock) {
    this.storage = storage;
    this.extractor = extractor;
    this.clock = clock;
  }

  @Override
  protected boolean process(IngestionContext ctx) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
