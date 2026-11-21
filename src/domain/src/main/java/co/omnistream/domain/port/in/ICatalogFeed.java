package co.omnistream.domain.port.in;

import co.omnistream.domain.model.access.UserId;

/** Driving port: implemented by a domain service, called by primary adapters. */
public interface ICatalogFeed {
  FeedSubscription follow(UserId u, CatalogChangeListener listener);
}
