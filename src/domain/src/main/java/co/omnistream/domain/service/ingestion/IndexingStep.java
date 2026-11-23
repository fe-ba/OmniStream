package co.omnistream.domain.service.ingestion;

import java.time.Instant;
import java.util.Objects;

/**
 * Concrete handler of the ingestion chain: indexes the file and applies classification (Chain of
 * Responsibility, ADR 0032).
 */
public final class IndexingStep extends IngestionStep {
  @Override
  protected boolean process(IngestionContext ctx) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(ctx.catalog, "ctx.catalog");
    Objects.requireNonNull(ctx.file, "ctx.file");
    Instant now = ctx.file.toSnapshot().ingestedAt();
    ctx.result = ctx.catalog.ingest(ctx.file, now);
    if (ctx.result.wasIndexed() && ctx.classification != null && !ctx.classification.isEmpty()) {
      ctx.catalog.applyClassification(ctx.result.fileId(), ctx.classification, now);
    }
    return true;
  }
}
