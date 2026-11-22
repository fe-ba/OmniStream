package co.omnistream.domain.service.ingestion;

import co.omnistream.domain.model.catalog.DuplicateAction;
import co.omnistream.domain.model.catalog.IngestionResult;
import co.omnistream.domain.model.catalog.MediaFile;
import co.omnistream.domain.policy.DeduplicationPolicy;
import java.util.Objects;
import java.util.Optional;

/** Concrete handler of the ingestion chain: stops rejected duplicates (Chain of Responsibility). */
public final class DuplicatesStep extends IngestionStep {
  private final DeduplicationPolicy policy;

  public DuplicatesStep(DeduplicationPolicy policy) {
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  @Override
  protected boolean process(IngestionContext ctx) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(ctx.catalog, "ctx.catalog");
    Objects.requireNonNull(ctx.file, "ctx.file");
    if (policy.isDuplicate(ctx.file, ctx.catalog)) {
      MediaFile existing =
          ctx.catalog
              .findByFingerprint(ctx.file.toSnapshot().fingerprint())
              .orElseThrow(
                  () -> new IllegalStateException("Duplicate reported without matching file"));
      if (policy.resolve(ctx.file, existing) == DuplicateAction.REJECT) {
        ctx.result =
            new IngestionResult(
                existing.toSnapshot().id(), true, Optional.of(DuplicateAction.REJECT));
        return false;
      }
    }
    return true;
  }
}
