package co.omnistream.domain.port.dto;

import co.omnistream.domain.model.catalog.ItemId;
import java.util.List;

/** Data transfer object of the ports. */
public record SyncDelta(List<ItemId> added, List<ItemId> modified, List<ItemId> removed) {
  public boolean isEmpty() {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
