package co.omnistream.domain.service;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.dto.CollectionCommand;
import co.omnistream.domain.port.dto.SearchCriteria;
import co.omnistream.domain.port.in.ISearch;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.snapshot.CatalogItemSnapshot;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.time.Clock;
import java.util.List;

/** Domain service that implements the ISearch driving port. */
public final class SearchService extends AuthorizedService implements ISearch {
  private final ICatalogRepository catalogRepository;

  public SearchService(
      IUserRepository userRepository,
      AccessPolicy accessPolicy,
      Clock clock,
      ICatalogRepository catalogRepository) {
    super(userRepository, accessPolicy, clock);
    this.catalogRepository = catalogRepository;
  }

  @Override
  public List<CatalogItemSnapshot> searchContent(UserId u, String txt) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public List<MediaFileSnapshot> filterByCriteria(UserId u, SearchCriteria c) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public List<CatalogItemSnapshot> browseLibrary(UserId u, ItemId col) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  /**
   * When the command creates a collection, grant its creator {@code PermissionLevel.MANAGE} on it,
   * for the same reason as in {@code CatalogService.ingestFile}: access is denied by default.
   */
  @Override
  public CollectionSnapshot manageCollections(UserId u, CollectionCommand cmd) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void tagFile(UserId u, ItemId id, Tag e) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  private List<MediaFileSnapshot> sortResults(List<MediaFileSnapshot> l, SearchCriteria c) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
