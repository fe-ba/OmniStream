package co.omnistream.domain.port.out;

import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.MetadataEntry;
import java.nio.file.Path;
import java.util.List;

/** Driven port: defined by the domain, implemented by secondary adapters. */
public interface IMetadataExtractor {
  boolean supports(MediaType type);

  List<MetadataEntry> extract(Path path);
}
