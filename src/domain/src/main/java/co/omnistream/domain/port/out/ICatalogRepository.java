package co.omnistream.domain.port.out;

import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.SearchCriteria;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.util.List;
import java.util.Optional;

/** Driven port: defined by the domain, implemented by secondary adapters. */
public interface ICatalogRepository {
  CatalogIndex getCatalog();

  void save(CatalogIndex c);

  Optional<MediaFileSnapshot> findFile(ItemId id);

  Optional<MediaFileSnapshot> findByFingerprint(Fingerprint h);

  List<MediaFileSnapshot> findByText(String txt);

  List<MediaFileSnapshot> findByCriteria(SearchCriteria c);
}
