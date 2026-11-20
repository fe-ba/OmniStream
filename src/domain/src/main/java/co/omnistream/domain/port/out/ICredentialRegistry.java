package co.omnistream.domain.port.out;

import co.omnistream.domain.model.access.LinkedIdentity;

/** Driven port: defined by the domain, implemented by secondary adapters. */
public interface ICredentialRegistry {
  LinkedIdentity register(String username, String password);

  void changePassword(LinkedIdentity i, String newPassword);
}
