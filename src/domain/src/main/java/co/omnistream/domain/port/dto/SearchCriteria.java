package co.omnistream.domain.port.dto;

import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.Tag;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Filters for {@code filterByCriteria}; every filter is optional, and those present must all match.
 *
 * @param text words in the name or the metadata
 * @param type only files of this type
 * @param tags only files with all these tags; empty for no tag filter. Compare them by {@link
 *     Tag#normalized()}: their source does not matter when filtering
 */
public record SearchCriteria(Optional<String> text, Optional<MediaType> type, Set<Tag> tags) {

  public SearchCriteria {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(type, "type");
    tags = Set.copyOf(tags);
  }

  /** Whether at least one filter is set. */
  public boolean hasFilters() {
    return text.isPresent() || type.isPresent() || !tags.isEmpty();
  }
}
