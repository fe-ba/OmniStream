package co.omnistream.domain.model.catalog;

import java.util.Set;

/** Value object. */
public record Classification(Set<Tag> tags, Set<ItemId> collections) {
  public static Classification empty() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Classification combine(Classification other) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public boolean isEmpty() {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
