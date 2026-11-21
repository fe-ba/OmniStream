package co.omnistream.domain.service.audit;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.port.dto.CollectionCommand;
import co.omnistream.domain.port.dto.SearchCriteria;
import co.omnistream.domain.port.in.ISearch;
import co.omnistream.domain.port.out.IAuditLog;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.snapshot.CatalogItemSnapshot;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Records collections and tags in the audit trail, and passes every call on to the search service
 * (Decorator, ADR 0025). Searching and browsing are not recorded.
 */
public final class AuditedSearch extends AuditingDecorator implements ISearch {
  private final ISearch delegate;

  public AuditedSearch(ISearch delegate, IAuditLog auditLog, IUnitOfWork unitOfWork, Clock clock) {
    super(auditLog, unitOfWork, clock);
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  public List<CatalogItemSnapshot> searchContent(UserId u, String txt) {
    return delegate.searchContent(u, txt);
  }

  @Override
  public List<MediaFileSnapshot> filterByCriteria(UserId u, SearchCriteria c) {
    return delegate.filterByCriteria(u, c);
  }

  @Override
  public List<CatalogItemSnapshot> browseLibrary(UserId u) {
    return delegate.browseLibrary(u);
  }

  @Override
  public List<CatalogItemSnapshot> browseLibrary(UserId u, ItemId col) {
    return delegate.browseLibrary(u, col);
  }

  /** A command without a collection creates one; with one, it renames it or adds items to it. */
  @Override
  public CollectionSnapshot manageCollections(UserId u, CollectionCommand cmd) {
    AuditAction action =
        cmd.collectionId().isEmpty()
            ? AuditAction.COLLECTION_CREATED
            : AuditAction.COLLECTION_MODIFIED;
    return audited(
        () -> delegate.manageCollections(u, cmd),
        collection -> {
          int added = cmd.itemsToAdd().size();
          String detail =
              added == 0
                  ? collection.name()
                  : collection.name()
                      + ", "
                      + added
                      + (added == 1 ? " item added" : " items added");
          return Optional.of(entry(u, action).about(collection.id()).saying(detail));
        });
  }

  @Override
  public void tagFile(UserId u, ItemId id, Tag e) {
    audited(
        () -> delegate.tagFile(u, id, e),
        () -> entry(u, AuditAction.FILE_TAGGED).about(id).saying(e.name()));
  }
}
