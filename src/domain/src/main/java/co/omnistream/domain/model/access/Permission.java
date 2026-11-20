package co.omnistream.domain.model.access;

import co.omnistream.domain.model.catalog.ItemId;

/** Value object. */
public record Permission(ItemId resource, PermissionLevel level) {
  public boolean allows(AccessType access) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public boolean appliesTo(ItemId resource) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
