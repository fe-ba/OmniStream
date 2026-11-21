package co.omnistream.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.event.CollectionCreated;
import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.event.FileDeleted;
import co.omnistream.domain.event.FileModified;
import co.omnistream.domain.event.ICatalogObserver;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.CatalogId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.IndexingStatus;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.dto.CatalogChange;
import co.omnistream.domain.port.in.FeedSubscription;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.IEventSubscriber;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class CatalogFeedServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-21T17:00:00Z");

  /** The event bus, reduced to its list of observers. */
  private static final class Bus implements IEventSubscriber {
    final List<ICatalogObserver> observers = new CopyOnWriteArrayList<>();

    @Override
    public void subscribe(ICatalogObserver observer) {
      observers.add(observer);
    }

    @Override
    public void unsubscribe(ICatalogObserver observer) {
      observers.remove(observer);
    }

    void publish(DomainEvent e) {
      for (ICatalogObserver observer : observers) {
        observer.onCatalogChanged(e);
      }
    }
  }

  /** Users kept in a map, saved as snapshots so that a later change needs a new save. */
  private static final class Users implements IUserRepository {
    private final Map<UserId, User> users = new HashMap<>();

    @Override
    public void save(User u) {
      users.put(u.toSnapshot().id(), User.fromSnapshot(u.toSnapshot()));
    }

    @Override
    public Optional<User> findById(UserId id) {
      return Optional.ofNullable(users.get(id)).map(u -> User.fromSnapshot(u.toSnapshot()));
    }

    @Override
    public Optional<User> findByUsername(String username) {
      throw new AssertionError("not used");
    }

    @Override
    public Optional<User> findByIdentity(LinkedIdentity i) {
      throw new AssertionError("not used");
    }

    @Override
    public List<User> findAll() {
      throw new AssertionError("not used");
    }
  }

  /** A catalog repository that only answers getCatalog(). */
  private static ICatalogRepository catalogOf(CatalogIndex index) {
    return (ICatalogRepository)
        Proxy.newProxyInstance(
            ICatalogRepository.class.getClassLoader(),
            new Class<?>[] {ICatalogRepository.class},
            (proxy, method, args) -> {
              if (method.getName().equals("getCatalog")) {
                return CatalogIndex.fromSnapshot(index.toSnapshot());
              }
              throw new AssertionError("not used: " + method.getName());
            });
  }

  private static MediaFileSnapshot file(ItemId id, String name, char seed) {
    return new MediaFileSnapshot(
        id,
        name,
        Path.of("/media/" + name),
        new Fingerprint("SHA-256", String.valueOf(seed).repeat(64)),
        MediaType.DOCUMENT,
        1024,
        Duration.ZERO,
        IndexingStatus.INDEXED,
        NOW,
        List.of(),
        Set.of());
  }

  private final Bus bus = new Bus();
  private final Users users = new Users();
  private final ItemId concerts = new ItemId(UUID.randomUUID());
  private final ItemId setlist = new ItemId(UUID.randomUUID());
  private final ItemId elsewhere = new ItemId(UUID.randomUUID());
  private final ICatalogRepository catalog =
      catalogOf(
          CatalogIndex.fromSnapshot(
              new CatalogSnapshot(
                  new CatalogId(UUID.randomUUID()),
                  "library",
                  1,
                  List.of(file(setlist, "Setlist.pdf", 'a'), file(elsewhere, "Taxes.pdf", 'b')),
                  List.of(new CollectionSnapshot(concerts, "Concerts", "", List.of(setlist))))));
  private final CatalogFeedService feed =
      new CatalogFeedService(
          users, new AccessPolicy(), Clock.fixed(NOW, ZoneOffset.UTC), bus, catalog);

  private final List<CatalogChange> heard = new ArrayList<>();

  private User reader() {
    User ana =
        User.create("ana", Role.USER, new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, "ana"));
    ana.grant(new Permission(concerts, PermissionLevel.READ));
    users.save(ana);
    return ana;
  }

  private static UserId id(User u) {
    return u.toSnapshot().id();
  }

  @Test
  void aFollowerHearsTheChangesItCanSeeThroughItsCollections() {
    feed.follow(id(reader()), heard::add);

    bus.publish(new FileModified(setlist, NOW));
    bus.publish(new CollectionCreated(concerts, NOW));

    assertThat(heard).hasSize(2);
    CatalogChange first = heard.get(0);
    assertThat(first.item()).isEqualTo(setlist);
    assertThat(first.change().name()).isEqualTo("MODIFIED");
    assertThat(first.event()).isEqualTo("FileModified");
    assertThat(first.at()).isEqualTo(NOW);
  }

  @Test
  void changesItCannotSeeAreNotPassedOn() {
    feed.follow(id(reader()), heard::add);

    bus.publish(new FileModified(elsewhere, NOW));

    assertThat(heard).isEmpty();
  }

  @Test
  void aRemovalIsPassedOnToEveryone() {
    feed.follow(id(reader()), heard::add);

    bus.publish(new FileDeleted(elsewhere, NOW));

    assertThat(heard).hasSize(1);
    assertThat(heard.get(0).change().name()).isEqualTo("REMOVED");
  }

  @Test
  void eventsThatAreNotAboutTheCatalogAreIgnored() {
    feed.follow(id(reader()), heard::add);

    bus.publish(new DomainEvent(new co.omnistream.domain.event.EventId(UUID.randomUUID()), NOW) {});

    assertThat(heard).isEmpty();
  }

  @Test
  void closingTheSubscriptionTakesTheObserverOffTheBus() {
    FeedSubscription subscription = feed.follow(id(reader()), heard::add);
    assertThat(bus.observers).hasSize(1);

    subscription.close();
    subscription.close();
    bus.publish(new FileModified(setlist, NOW));

    assertThat(bus.observers).isEmpty();
    assertThat(heard).isEmpty();
  }

  @Test
  void permissionsAreReadAgainForEachChange() {
    User ana = reader();
    feed.follow(id(ana), heard::add);

    ana.revoke(new Permission(concerts, PermissionLevel.READ));
    users.save(ana);
    bus.publish(new FileModified(setlist, NOW));

    assertThat(heard).isEmpty();
  }

  @Test
  void aDeactivatedFollowerStopsFollowing() {
    User ana = reader();
    feed.follow(id(ana), heard::add);

    ana.deactivate();
    users.save(ana);
    bus.publish(new FileModified(setlist, NOW));

    assertThat(heard).isEmpty();
    assertThat(bus.observers).isEmpty();
  }

  @Test
  void aListenerThatFailsStopsFollowingWithoutBreakingTheOthers() {
    UserId ana = id(reader());
    feed.follow(
        ana,
        change -> {
          throw new IllegalStateException("connection closed");
        });
    feed.follow(ana, heard::add);

    bus.publish(new FileModified(setlist, NOW));

    assertThat(heard).hasSize(1);
    assertThat(bus.observers).hasSize(1);
  }

  @Test
  void onlyAnActiveUserMayFollow() {
    User gone = reader();
    gone.deactivate();
    users.save(gone);

    assertThatThrownBy(() -> feed.follow(id(gone), heard::add))
        .isInstanceOf(ResourceNotFound.class);
    assertThatThrownBy(() -> feed.follow(new UserId(UUID.randomUUID()), heard::add))
        .isInstanceOf(ResourceNotFound.class);
    assertThat(bus.observers).isEmpty();
  }
}
