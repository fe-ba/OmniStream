package co.omnistream.domain.service.ingestion;

import co.omnistream.domain.service.classification.ClassificationRule;
import java.util.List;

/** Concrete handler of the ingestion chain (Chain of Responsibility). */
public final class ClassificationStep extends IngestionStep {
  private final List<ClassificationRule> rules;

  public ClassificationStep(List<ClassificationRule> rules) {
    this.rules = rules;
  }

  @Override
  protected boolean process(IngestionContext ctx) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
