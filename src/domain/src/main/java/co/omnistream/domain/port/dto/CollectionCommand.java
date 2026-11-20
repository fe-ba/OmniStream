package co.omnistream.domain.port.dto;

import co.omnistream.domain.model.catalog.ItemId;
import java.util.List;

/** Command of the ports. */
public record CollectionCommand(ItemId collectionId, String name, List<ItemId> itemsToAdd) {}
