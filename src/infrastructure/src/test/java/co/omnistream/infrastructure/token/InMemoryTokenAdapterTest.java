package co.omnistream.infrastructure.token;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.port.dto.SessionToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryTokenAdapterTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void savesFindsAndDeletesAToken() {
    InMemoryTokenAdapter adapter = new InMemoryTokenAdapter(CLOCK);
    SessionToken token = token("abc", NOW.plus(Duration.ofHours(1)));

    adapter.save(token);

    assertThat(adapter.find("abc")).contains(token);
    adapter.delete("abc");
    assertThat(adapter.find("abc")).isEmpty();
  }

  @Test
  void forgetsAnExpiredToken() {
    InMemoryTokenAdapter adapter = new InMemoryTokenAdapter(CLOCK);

    adapter.save(token("viejo", NOW.minusSeconds(1)));

    assertThat(adapter.find("viejo")).isEmpty();
  }

  @Test
  void forgetsAnExpiredTokenWhenAnotherOneIsSaved() {
    InMemoryTokenAdapter adapter = new InMemoryTokenAdapter(CLOCK);
    adapter.save(token("viejo", NOW.minusSeconds(1)));
    adapter.save(token("nuevo", NOW.plusSeconds(60)));

    assertThat(adapter.find("viejo")).isEmpty();
    assertThat(adapter.find("nuevo")).isPresent();
  }

  private static SessionToken token(String value, Instant expiresAt) {
    return new SessionToken(value, new UserId(UUID.randomUUID()), Role.USER, expiresAt);
  }
}
