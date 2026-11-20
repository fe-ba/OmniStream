package co.omnistream.domain.model.access;

import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.snapshot.UserSnapshot;
import java.util.Set;

/** Aggregate root. */
public final class User {
  private UserId id;
  private String username;
  private Role role;
  private Set<Permission> permissions;
  private Set<LinkedIdentity> identities;
  private boolean active;

  private User() {}

  public static User create(String username, Role role, LinkedIdentity i) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public static User createGuest(ItemId resource, PermissionLevel level) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public boolean canAccess(ItemId resource, AccessType access) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void grant(Permission p) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void revoke(Permission p) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void changeRole(Role r) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void link(LinkedIdentity i) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public boolean isAdmin() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void deactivate() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public UserSnapshot toSnapshot() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public static User fromSnapshot(UserSnapshot i) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
