package co.omnistream.domain.service.classification;

import co.omnistream.domain.model.catalog.Classification;
import co.omnistream.domain.snapshot.MediaFileSnapshot;

/** Strategy that decides the tags and collections of a file (Strategy, ADR 0016). */
public interface ClassificationRule {
  boolean appliesTo(MediaFileSnapshot a);

  Classification classify(MediaFileSnapshot a);
}
