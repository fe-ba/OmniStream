package co.omnistream.domain.port.out;

import co.omnistream.domain.model.access.LinkedIdentity;

/** Driven port: defined by the domain, implemented by secondary adapters. */
public interface ICredentialRegistry {
  /**
   * Stores the password (hashed) and returns {@code new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER,
   * username)}. The password never enters the domain.
   */
  LinkedIdentity register(String username, String password);

  void changePassword(LinkedIdentity i, String newPassword);
}
