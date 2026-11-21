package co.omnistream.domain.port.in;

import co.omnistream.domain.port.dto.CatalogChange;

/**
 * What a primary adapter gives {@link ICatalogFeed#follow} to hear about changes (ADR 0026). It is
 * called on the thread that delivers the events, so it should hand the change on and return.
 */
@FunctionalInterface
public interface CatalogChangeListener {
  void onChange(CatalogChange c);
}
