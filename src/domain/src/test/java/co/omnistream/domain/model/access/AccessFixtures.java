package co.omnistream.domain.model.access;

import co.omnistream.domain.model.catalog.ItemId;
import java.util.UUID;

/** Objects shared by the access tests. */
public final class AccessFixtures {

  private AccessFixtures() {}

  /** A new random item identifier. */
  public static ItemId item() {
    return new ItemId(UUID.randomUUID());
  }

  /** A new active user with a local identity and the given role. */
  public static User user(String username, Role role) {
    return User.create(username, role, local(username));
  }

  /** The local identity of {@code username}. */
  public static LinkedIdentity local(String username) {
    return new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, username);
  }
}
