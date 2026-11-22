package co.omnistream.domain.service;

import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.CatalogItem;
import co.omnistream.domain.model.catalog.Collection;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.dto.CollectionCommand;
import co.omnistream.domain.port.dto.SearchCriteria;
import co.omnistream.domain.port.in.ISearch;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.snapshot.CatalogItemSnapshot;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import co.omnistream.domain.snapshot.UserSnapshot;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Domain service that implements the ISearch driving port. */
public final class SearchService extends AuthorizedService implements ISearch {
  private static final Comparator<CatalogItemSnapshot> ITEM_ORDER =
      Comparator.comparing((CatalogItemSnapshot item) -> item.name().toLowerCase(Locale.ROOT))
          .thenComparing(item -> item.id().value());

  private final ICatalogRepository catalogRepository;
  private final IUnitOfWork unitOfWork;

  public SearchService(
      IUserRepository userRepository,
      AccessPolicy accessPolicy,
      Clock clock,
      ICatalogRepository catalogRepository,
      IUnitOfWork unitOfWork) {
    super(userRepository, accessPolicy, clock);
    this.catalogRepository = Objects.requireNonNull(catalogRepository, "catalogRepository");
    this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
  }

  @Override
  public List<CatalogItemSnapshot> searchContent(UserId u, String txt) {
    Objects.requireNonNull(txt, "txt");
    User user = requireActiveUser(u);
    String query = txt.strip();
    if (query.isEmpty()) {
      return List.of();
    }
    CatalogSnapshot snapshot = catalogRepository.getCatalog().toSnapshot();
    String needle = query.toLowerCase(Locale.ROOT);
    Map<ItemId, CatalogItemSnapshot> candidates = new LinkedHashMap<>();
    for (CollectionSnapshot collection : snapshot.collections()) {
      if (matchesCollection(collection, needle)) {
        candidates.put(collection.id(), collection);
      }
    }
    for (MediaFileSnapshot file : catalogRepository.findByText(query)) {
      candidates.putIfAbsent(file.id(), file);
    }
    return candidates.values().stream()
        .filter(item -> accessPolicy.authorizes(user, item.id(), AccessType.VIEW, snapshot))
        .sorted(ITEM_ORDER)
        .toList();
  }

  @Override
  public List<MediaFileSnapshot> filterByCriteria(UserId u, SearchCriteria c) {
    Objects.requireNonNull(c, "criteria");
    User user = requireActiveUser(u);
    CatalogSnapshot snapshot = catalogRepository.getCatalog().toSnapshot();
    List<MediaFileSnapshot> candidates =
        c.hasFilters() ? catalogRepository.findByCriteria(c) : snapshot.files();
    List<MediaFileSnapshot> visible =
        candidates.stream()
            .filter(file -> accessPolicy.authorizes(user, file.id(), AccessType.VIEW, snapshot))
            .toList();
    return sortResults(visible, c);
  }

  /**
   * The top level of the library: the files and collections that are not inside any collection,
   * only those {@code u} may view ({@code AccessType.VIEW}).
   */
  @Override
  public List<CatalogItemSnapshot> browseLibrary(UserId u) {
    User user = requireActiveUser(u);
    CatalogSnapshot snapshot = catalogRepository.getCatalog().toSnapshot();
    Set<ItemId> nested = new HashSet<>();
    for (CollectionSnapshot collection : snapshot.collections()) {
      nested.addAll(collection.items());
    }
    List<CatalogItemSnapshot> topLevel = new ArrayList<>();
    snapshot.collections().stream()
        .filter(collection -> !nested.contains(collection.id()))
        .filter(
            collection -> accessPolicy.authorizes(user, collection.id(), AccessType.VIEW, snapshot))
        .sorted(ITEM_ORDER)
        .forEach(topLevel::add);
    snapshot.files().stream()
        .filter(file -> !nested.contains(file.id()))
        .filter(file -> accessPolicy.authorizes(user, file.id(), AccessType.VIEW, snapshot))
        .sorted(ITEM_ORDER)
        .forEach(topLevel::add);
    return List.copyOf(topLevel);
  }

  @Override
  public List<CatalogItemSnapshot> browseLibrary(UserId u, ItemId col) {
    Objects.requireNonNull(col, "collectionId");
    CatalogSnapshot snapshot = catalogRepository.getCatalog().toSnapshot();
    checkAccess(u, col, AccessType.VIEW, snapshot);
    CollectionSnapshot target =
        snapshot.collections().stream()
            .filter(collection -> collection.id().equals(col))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFound("Collection not found: " + col));
    User user = loadUser(u);
    Map<ItemId, CatalogItemSnapshot> byId = new HashMap<>();
    for (CollectionSnapshot collection : snapshot.collections()) {
      byId.put(collection.id(), collection);
    }
    for (MediaFileSnapshot file : snapshot.files()) {
      byId.put(file.id(), file);
    }
    return target.items().stream()
        .map(byId::get)
        .filter(Objects::nonNull)
        .filter(item -> accessPolicy.authorizes(user, item.id(), AccessType.VIEW, snapshot))
        .toList();
  }

  /**
   * When the command creates a collection, grant its creator {@code PermissionLevel.MANAGE} on it,
   * for the same reason as in {@code CatalogService.ingestFile}: access is denied by default.
   */
  @Override
  public CollectionSnapshot manageCollections(UserId u, CollectionCommand cmd) {
    Objects.requireNonNull(cmd, "command");
    return unitOfWork.execute(() -> {
      User actor = requireActiveNonGuest(u);
      CatalogIndex catalog = catalogRepository.getCatalog();
      CatalogSnapshot snapshot = catalog.toSnapshot();
      for (ItemId itemId : cmd.itemsToAdd()) {
        if (!accessPolicy.authorizes(actor, itemId, AccessType.VIEW, snapshot)) {
          throw new AccessDenied("User " + u + " does not have VIEW permission on item " + itemId);
        }
      }
      boolean creating = cmd.collectionId().isEmpty();
      Collection collection =
          creating ? createCollection(cmd, catalog) : updateCollection(u, cmd, catalog, snapshot);
      catalogRepository.save(catalog);
      if (creating && actor.toSnapshot().role() == Role.USER) {
        actor.grant(new Permission(collection.toSnapshot().id(), PermissionLevel.MANAGE));
        userRepository.save(actor);
      }
      return collection.toSnapshot();
    });
  }

  @Override
  public void tagFile(UserId u, ItemId id, Tag e) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(e, "tag");
    unitOfWork.execute(() -> {
      CatalogIndex catalog = catalogRepository.getCatalog();
      checkAccess(u, id, AccessType.MODIFY, catalog.toSnapshot());
      catalog.tag(id, e, clock.instant());
      catalogRepository.save(catalog);
      return null;
    });
  }

  private Collection createCollection(CollectionCommand cmd, CatalogIndex catalog) {
    String name =
        cmd.name().orElseThrow(() -> new IllegalArgumentException("Collection name is required"));
    Collection created = catalog.createCollection(name, clock.instant());
    ItemId createdId = created.toSnapshot().id();
    for (ItemId itemId : cmd.itemsToAdd()) {
      catalog.addToCollection(createdId, itemId, clock.instant());
    }
    return created;
  }

  private Collection updateCollection(
      UserId u, CollectionCommand cmd, CatalogIndex catalog, CatalogSnapshot snapshot) {
    ItemId collectionId = cmd.collectionId().orElseThrow();
    checkAccess(u, collectionId, AccessType.MODIFY, snapshot);
    CatalogItem existing =
        catalog
            .findItem(collectionId)
            .filter(Collection.class::isInstance)
            .orElseThrow(() -> new ResourceNotFound("Collection not found: " + collectionId));
    Collection collection = (Collection) existing;
    if (cmd.name().isPresent()) {
      collection.rename(cmd.name().get());
    }
    for (ItemId itemId : cmd.itemsToAdd()) {
      catalog.addToCollection(collectionId, itemId, clock.instant());
    }
    return collection;
  }

  private User requireActiveUser(UserId u) {
    User user = loadUser(u);
    if (!user.toSnapshot().active()) {
      throw new AccessDenied("User account is deactivated");
    }
    return user;
  }

  private User requireActiveNonGuest(UserId u) {
    User user = loadUser(u);
    UserSnapshot snapshot = user.toSnapshot();
    if (!snapshot.active() || snapshot.role() == Role.GUEST) {
      throw new AccessDenied("Only active non-guest users can manage collections");
    }
    return user;
  }

  private static boolean matchesCollection(CollectionSnapshot collection, String needle) {
    return collection.name().toLowerCase(Locale.ROOT).contains(needle)
        || collection.description().toLowerCase(Locale.ROOT).contains(needle);
  }

  private List<MediaFileSnapshot> sortResults(List<MediaFileSnapshot> l, SearchCriteria c) {
    Comparator<MediaFileSnapshot> order =
        Comparator.comparing((MediaFileSnapshot file) -> file.name().toLowerCase(Locale.ROOT))
            .thenComparing(MediaFileSnapshot::ingestedAt)
            .thenComparing(file -> file.id().value());
    return l.stream().sorted(order).toList();
  }
}
