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
  /**
   * The catalog. The first time, when nothing is stored yet, build it with {@code
   * CatalogIndex.fromSnapshot} from an empty snapshot with version 0.
   */
  CatalogIndex getCatalog();

  /**
   * Saves the catalog if its {@code version} is still the stored one, and stores it with {@code
   * version + 1}; otherwise throws {@code VersionConflict}. The domain never changes the version.
   */
  void save(CatalogIndex c);

  Optional<MediaFileSnapshot> findFile(ItemId id);

  Optional<MediaFileSnapshot> findByFingerprint(Fingerprint h);

  List<MediaFileSnapshot> findByText(String txt);

  List<MediaFileSnapshot> findByCriteria(SearchCriteria c);
}
