package co.omnistream.domain.port.in;

/**
 * Following the catalog, as {@link ICatalogFeed#follow} started it. Closing it stops the changes;
 * closing it again does nothing.
 */
@FunctionalInterface
public interface FeedSubscription extends AutoCloseable {
  @Override
  void close();
}
