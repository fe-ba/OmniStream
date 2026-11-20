package co.omnistream.domain.service.ingestion;

import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.Classification;
import co.omnistream.domain.model.catalog.IngestionResult;
import co.omnistream.domain.model.catalog.MediaFile;
import java.nio.file.Path;

/** Data transfer object of the ports. */
public final class IngestionContext {
  public CatalogIndex catalog;
  public Path path;
  public MediaFile file;
  public Classification classification;
  public IngestionResult result;
}
