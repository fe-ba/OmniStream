package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.port.out.ICredentialRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory credential registry for the domain tests (ADR 0018).
 *
 * <p>It keeps the password as it is given: hashing belongs to the real adapter, and the domain
 * never sees the password after registration.
 */
public final class InMemoryCredentialRegistry implements ICredentialRegistry {

  private final Map<String, String> passwords = new LinkedHashMap<>();

  @Override
  public LinkedIdentity register(String username, String password) {
    String name = required(username, "username");
    String secret = required(password, "password");
    if (passwords.containsKey(name)) {
      throw new IllegalArgumentException("The username " + name + " is already registered");
    }
    passwords.put(name, secret);
    return new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, name);
  }

  @Override
  public void changePassword(LinkedIdentity identity, String newPassword) {
    Objects.requireNonNull(identity, "identity");
    if (!identity.isLocal()) {
      throw new IllegalArgumentException(
          "Only a local identity has a password: " + identity.issuer());
    }
    String secret = required(newPassword, "password");
    if (!passwords.containsKey(identity.subject())) {
      throw new ResourceNotFound("No local account named " + identity.subject());
    }
    passwords.put(identity.subject(), secret);
  }

  /** The stored passwords, for the assertions of a test. */
  public Map<String, String> passwords() {
    return Map.copyOf(passwords);
  }

  private static String required(String value, String name) {
    String stripped = Objects.requireNonNull(value, name).strip();
    if (stripped.isEmpty()) {
      throw new IllegalArgumentException("A password account needs a " + name);
    }
    return stripped;
  }
}
