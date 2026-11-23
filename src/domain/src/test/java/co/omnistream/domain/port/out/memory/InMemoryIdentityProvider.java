package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.port.dto.Credentials;
import co.omnistream.domain.port.dto.LocalCredentials;
import co.omnistream.domain.port.out.IIdentityProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory identity provider for the domain tests (ADR 0018).
 *
 * <p>It returns the identity registered for a username, without checking a password: the tests
 * decide who is recognized.
 */
public final class InMemoryIdentityProvider implements IIdentityProvider {

  private final String provider;
  private final Map<String, LinkedIdentity> identities = new LinkedHashMap<>();

  /** A provider of local credentials. */
  public InMemoryIdentityProvider() {
    this("local");
  }

  public InMemoryIdentityProvider(String provider) {
    this.provider = Objects.requireNonNull(provider, "provider");
  }

  /** Recognizes {@code username} as {@code identity}. */
  public InMemoryIdentityProvider add(String username, LinkedIdentity identity) {
    identities.put(Objects.requireNonNull(username, "username").strip(), identity);
    return this;
  }

  @Override
  public boolean supports(Credentials credentials) {
    return credentials != null && provider.equals(credentials.provider());
  }

  @Override
  public Optional<LinkedIdentity> verify(Credentials credentials) {
    if (!supports(credentials) || !(credentials instanceof LocalCredentials local)) {
      return Optional.empty();
    }
    return Optional.ofNullable(identities.get(local.username().strip()));
  }
}
