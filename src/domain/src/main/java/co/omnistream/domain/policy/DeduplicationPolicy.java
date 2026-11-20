package co.omnistream.domain.policy;

import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.DuplicateAction;
import co.omnistream.domain.model.catalog.MediaFile;

/** Stateless domain policy. */
public final class DeduplicationPolicy {
  public DeduplicationPolicy() {}

  public boolean isDuplicate(MediaFile a, CatalogIndex c) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public DuplicateAction resolve(MediaFile incoming, MediaFile existing) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
