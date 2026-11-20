package co.omnistream.domain.port.in;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.port.dto.CollectionCommand;
import co.omnistream.domain.port.dto.SearchCriteria;
import co.omnistream.domain.snapshot.CatalogItemSnapshot;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.util.List;

/** Driving port: implemented by a domain service, called by primary adapters. */
public interface ISearch {
  List<CatalogItemSnapshot> searchContent(UserId u, String txt);

  List<MediaFileSnapshot> filterByCriteria(UserId u, SearchCriteria c);

  List<CatalogItemSnapshot> browseLibrary(UserId u, ItemId col);

  CollectionSnapshot manageCollections(UserId u, CollectionCommand cmd);

  void tagFile(UserId u, ItemId id, Tag e);
}
