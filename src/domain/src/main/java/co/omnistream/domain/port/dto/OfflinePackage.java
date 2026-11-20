package co.omnistream.domain.port.dto;

import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MetadataEntry;
import java.nio.file.Path;
import java.util.List;

/** Data transfer object of the ports. */
public record OfflinePackage(ItemId fileId, List<MetadataEntry> metadata, Path content) {}
