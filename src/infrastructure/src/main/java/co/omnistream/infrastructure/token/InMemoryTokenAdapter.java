package co.omnistream.infrastructure.token;

import co.omnistream.domain.port.dto.SessionToken;
import co.omnistream.domain.port.out.ITokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Secondary adapter that keeps the session tokens in memory (ADR 0015).
 *
 * <p>Tokens do not need to survive a restart: whoever was logged in logs in again. Expired tokens
 * are dropped when they are looked up and when a new one is saved, so the map does not grow.
 */
public final class InMemoryTokenAdapter implements ITokenRepository {

  private final Map<String, SessionToken> tokens = new ConcurrentHashMap<>();
  private final Clock clock;

  public InMemoryTokenAdapter(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public void save(SessionToken t) {
    Objects.requireNonNull(t, "t");
    purgeExpired();
    tokens.put(t.value(), t);
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

  /** Drops every token that is no longer valid. */
  private void purgeExpired() {
    Instant now = clock.instant();
    tokens.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
  }
}
