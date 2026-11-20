package co.omnistream.domain.service.classification;

import co.omnistream.domain.model.catalog.Classification;
import co.omnistream.domain.snapshot.MediaFileSnapshot;

/** Concrete classification rule: by media type (Strategy, ADR 0016). */
public final class MediaTypeRule implements ClassificationRule {
  @Override
  public boolean appliesTo(MediaFileSnapshot a) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Classification classify(MediaFileSnapshot a) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
