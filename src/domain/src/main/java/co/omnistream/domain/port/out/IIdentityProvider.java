package co.omnistream.domain.port.out;

import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.port.dto.Credentials;
import java.util.Optional;

/** Driven port and strategy for verifying credentials (Strategy). */
public interface IIdentityProvider {
  boolean supports(Credentials c);

  Optional<LinkedIdentity> verify(Credentials c);
}
