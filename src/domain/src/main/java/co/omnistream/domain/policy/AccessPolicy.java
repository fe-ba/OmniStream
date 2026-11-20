package co.omnistream.domain.policy;

import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.catalog.ItemId;

/** Stateless domain policy. */
public final class AccessPolicy {
  public AccessPolicy() {}

  public boolean authorizes(User u, ItemId resource, AccessType access) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public PermissionLevel effectiveLevel(User u, ItemId resource) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
