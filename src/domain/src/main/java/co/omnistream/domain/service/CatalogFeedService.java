package co.omnistream.domain.service;

import co.omnistream.domain.event.CatalogEvent;
import co.omnistream.domain.event.ChangeType;
import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.event.ICatalogObserver;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.dto.CatalogChange;
import co.omnistream.domain.port.in.CatalogChangeListener;
import co.omnistream.domain.port.in.FeedSubscription;
import co.omnistream.domain.port.in.ICatalogFeed;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.IEventSubscriber;
import co.omnistream.domain.port.out.IUserRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Domain service that implements the ICatalogFeed driving port: it tells each person following the
 * catalog about the changes they can see, as they happen (ADR 0026).
 *
 * <p>Each follower is an observer of its own on the event bus (Observer): the bus stays the only
 * owner of the observer list, and closing the subscription takes the observer off it.
 */
public final class CatalogFeedService extends AuthorizedService implements ICatalogFeed {
  private final IEventSubscriber eventSubscriber;
  private final ICatalogRepository catalogRepository;

  public CatalogFeedService(
      IUserRepository userRepository,
      AccessPolicy accessPolicy,
      Clock clock,
      IEventSubscriber eventSubscriber,
      ICatalogRepository catalogRepository) {
    super(userRepository, accessPolicy, clock);
    this.eventSubscriber = Objects.requireNonNull(eventSubscriber, "eventSubscriber");
    this.catalogRepository = Objects.requireNonNull(catalogRepository, "catalogRepository");
  }

  /**
   * Starts telling {@code listener} about the changes {@code u} can see.
   *
   * @throws ResourceNotFound if there is no such user, or it is deactivated
   */
  @Override
  public FeedSubscription follow(UserId u, CatalogChangeListener listener) {
    Objects.requireNonNull(u, "u");
    Objects.requireNonNull(listener, "listener");
    activeUser(u).orElseThrow(() -> new ResourceNotFound("No active user " + u.value()));
    Follower follower = new Follower(u, listener);
    eventSubscriber.subscribe(follower);
    return follower::stop;
  }

  private Optional<User> activeUser(UserId u) {
    return userRepository.findById(u).filter(user -> user.toSnapshot().active());
  }

  /** One person following the catalog: an observer that filters by what they can see. */
  private final class Follower implements ICatalogObserver {
    private final UserId u;
    private final CatalogChangeListener listener;
    private final AtomicBoolean stopped = new AtomicBoolean();

    Follower(UserId u, CatalogChangeListener listener) {
      this.u = u;
      this.listener = listener;
    }

    /**
     * Passes on the catalog changes the follower can see. The user and the catalog are read again
     * for each change, since permissions may have changed in the meantime. A removed item is passed
     * on to everyone: the catalog no longer says who could see it, and its identifier alone tells
     * nothing. A follower that was deactivated, or whose listener fails, stops following.
     */
    @Override
    public void onCatalogChanged(DomainEvent e) {
      if (stopped.get() || !(e instanceof CatalogEvent event)) {
        return;
      }
      if (event.changeType() != ChangeType.REMOVED && !canSee(event.item())) {
        return;
      }
      try {
        listener.onChange(CatalogChange.of(event));
      } catch (RuntimeException failure) {
        stop();
      }
    }

    private boolean canSee(ItemId item) {
      Optional<User> user = activeUser(u);
      if (user.isEmpty()) {
        stop();
        return false;
      }
      return accessPolicy.authorizes(
          user.get(), item, AccessType.VIEW, catalogRepository.getCatalog().toSnapshot());
    }

    void stop() {
      if (!stopped.getAndSet(true)) {
        eventSubscriber.unsubscribe(this);
      }
    }
  }
}
