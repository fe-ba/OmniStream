package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.port.dto.SessionToken;
import co.omnistream.domain.port.out.ITokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token repository for the domain tests (ADR 0018).
 *
 * <p>Like the real adapter, it drops a token when it is looked up after it expired and purges the
 * expired ones when another token is saved.
 */
public final class InMemoryTokenRepository implements ITokenRepository {

  private final Map<String, SessionToken> tokens = new ConcurrentHashMap<>();
  private final Clock clock;

  public InMemoryTokenRepository() {
    this(Clock.systemUTC());
  }

  public InMemoryTokenRepository(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public void save(SessionToken token) {
    Objects.requireNonNull(token, "token");
    purgeExpired();
    tokens.put(token.value(), token);
  }

  @Override
  public Optional<SessionToken> find(String value) {
    Objects.requireNonNull(value, "value");
    SessionToken token = tokens.get(value);
    if (token == null) {
      return Optional.empty();
    }
    if (!token.expiresAt().isAfter(clock.instant())) {
      tokens.remove(value, token);
      return Optional.empty();
    }
    return Optional.of(token);
  }

  @Override
  public void delete(String value) {
    Objects.requireNonNull(value, "value");
    tokens.remove(value);
  }

  private void purgeExpired() {
    Instant now = clock.instant();
    tokens.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
  }
}
