package co.omnistream.domain.port.in;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.IngestionResult;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.ServerStatus;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/** Driving port: implemented by a domain service, called by primary adapters. */
public interface ICatalogManagement {
  IngestionResult ingestFile(UserId u, Path path);

  IngestionResult uploadFile(UserId u, String fileName, InputStream data);

  int reindexLibrary(UserId u, Path root);

  List<MediaFileSnapshot> detectDuplicates(UserId u);

  void editMetadata(UserId u, ItemId id, String key, String value);

  void deleteFile(UserId u, ItemId id);

  Path backupCatalog(UserId admin, Path destination);

  void configureLibraryPath(UserId admin, Path path);

  ServerStatus monitorServerStatus(UserId admin);
}
