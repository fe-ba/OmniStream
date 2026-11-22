package co.omnistream.domain.service.ingestion;

import co.omnistream.domain.model.catalog.Classification;
import co.omnistream.domain.service.classification.ClassificationRule;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.util.List;
import java.util.Objects;

/**
 * Concrete handler of the ingestion chain: evaluates classification rules (Chain of Responsibility,
 * ADR 0016).
 */
public final class ClassificationStep extends IngestionStep {
  private final List<ClassificationRule> rules;

  public ClassificationStep(List<ClassificationRule> rules) {
    this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
  }

  @Override
  protected boolean process(IngestionContext ctx) {
    Objects.requireNonNull(ctx, "ctx");
    MediaFileSnapshot snapshot = Objects.requireNonNull(ctx.file, "ctx.file").toSnapshot();
    Classification combined = Classification.empty();
    for (ClassificationRule rule : rules) {
      if (rule.appliesTo(snapshot)) {
        combined = combined.combine(rule.classify(snapshot));
      }
    }
    ctx.classification = combined;
    return true;
  }
}
