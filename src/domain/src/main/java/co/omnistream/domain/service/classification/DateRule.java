package co.omnistream.domain.service.classification;

import co.omnistream.domain.model.catalog.Classification;
import co.omnistream.domain.snapshot.MediaFileSnapshot;

/** Concrete classification rule: by capture date (Strategy, ADR 0016). */
public final class DateRule implements ClassificationRule {
  @Override
  public boolean appliesTo(MediaFileSnapshot a) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Classification classify(MediaFileSnapshot a) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
