package co.omnistream.domain.port.dto;

import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.Tag;
import java.util.Set;

/** Data transfer object of the ports. */
public record SearchCriteria(String text, MediaType type, Set<Tag> tags) {
  public boolean hasFilters() {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
