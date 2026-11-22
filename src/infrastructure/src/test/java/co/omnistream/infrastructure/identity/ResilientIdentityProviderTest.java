package co.omnistream.infrastructure.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.port.dto.Credentials;
import co.omnistream.domain.port.dto.LocalCredentials;
import co.omnistream.domain.port.out.IIdentityProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResilientIdentityProviderTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
  private static final Credentials CREDENTIALS = new LocalCredentials("local", "ana", "x");

  @Test
  void opensAfterTheFailureThresholdAndSkipsTheProvider() {
    MutableClock clock = new MutableClock(NOW);
    FailingProvider delegate = new FailingProvider(10);
    ResilientIdentityProvider provider =
        new ResilientIdentityProvider(delegate, 2, Duration.ofMinutes(1), clock);

    assertThatThrownBy(() -> provider.verify(CREDENTIALS))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> provider.verify(CREDENTIALS))
        .isInstanceOf(IllegalStateException.class);

    assertThat(provider.state()).isEqualTo(CircuitState.OPEN);
    assertThat(provider.verify(CREDENTIALS)).isEmpty();
    assertThat(delegate.calls()).isEqualTo(2);
  }

  @Test
  void triesAgainAfterTheRetryWindowAndClosesOnSuccess() {
    MutableClock clock = new MutableClock(NOW);
    FailingProvider delegate = new FailingProvider(2);
    ResilientIdentityProvider provider =
        new ResilientIdentityProvider(delegate, 2, Duration.ofMinutes(1), clock);
    assertThatThrownBy(() -> provider.verify(CREDENTIALS))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> provider.verify(CREDENTIALS))
        .isInstanceOf(IllegalStateException.class);

    clock.advance(Duration.ofMinutes(2));

    assertThat(provider.state()).isEqualTo(CircuitState.HALF_OPEN);
    assertThat(provider.verify(CREDENTIALS)).contains(new LinkedIdentity("local", "ana"));
    assertThat(provider.state()).isEqualTo(CircuitState.CLOSED);
  }

  /** A provider that fails a given number of times and then recognizes the user. */
  private static final class FailingProvider implements IIdentityProvider {
    private final AtomicInteger calls = new AtomicInteger();
    private final int failures;

    private FailingProvider(int failures) {
      this.failures = failures;
    }

    int calls() {
      return calls.get();
    }

    @Override
    public boolean supports(Credentials c) {
      return true;
    }

    @Override
    public Optional<LinkedIdentity> verify(Credentials c) {
      if (calls.incrementAndGet() <= failures) {
        throw new IllegalStateException("provider down");
      }
      return Optional.of(new LinkedIdentity("local", "ana"));
    }
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    private MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
