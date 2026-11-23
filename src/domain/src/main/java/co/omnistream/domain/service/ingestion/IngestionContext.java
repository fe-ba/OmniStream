package co.omnistream.domain.service.ingestion;

import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.Classification;
import co.omnistream.domain.model.catalog.IngestionResult;
import co.omnistream.domain.model.catalog.MediaFile;
import java.nio.file.Path;

/** Mutable state carried along the ingestion chain ({@link IngestionStep}). */
public final class IngestionContext {
  public CatalogIndex catalog;
  public Path path;
  public MediaFile file;
  public Classification classification;
  public IngestionResult result;
}
