package co.omnistream.infrastructure.identity;

import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.port.dto.Credentials;
import co.omnistream.domain.port.out.IIdentityProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Decorator of an identity provider that stops calling it when it keeps failing (Circuit Breaker,
 * ADR 0009, version 2.0).
 *
 * <p>An external OIDC provider that is down would otherwise make every login wait for its timeout.
 * After {@code threshold} failures the circuit opens and the provider is skipped, so the next
 * providers of the list are tried; after the retry window one call is let through, and its result
 * decides whether the circuit closes or stays open.
 *
 * <p>{@link #verify} answers empty while the circuit is open, which means "this provider cannot
 * tell"; {@code AccessService} then tries the next one.
 */
public final class ResilientIdentityProvider implements IIdentityProvider {

  private final IIdentityProvider delegate;
  private final int threshold;
  private final Duration retryWindow;
  private final Clock clock;

  private CircuitState state = CircuitState.CLOSED;
  private int failures;
  private Instant openedAt;

  public ResilientIdentityProvider(
      IIdentityProvider delegate, int threshold, Duration retryWindow, Clock clock) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.retryWindow = Objects.requireNonNull(retryWindow, "retryWindow");
    this.clock = Objects.requireNonNull(clock, "clock");
    if (threshold < 1) {
      throw new IllegalArgumentException("The circuit needs a positive failure threshold");
    }
    if (!retryWindow.isPositive()) {
      throw new IllegalArgumentException("The circuit needs a positive retry window");
    }
    this.threshold = threshold;
  }

  @Override
  public boolean supports(Credentials c) {
    return delegate.supports(c);
  }

  @Override
  public synchronized Optional<LinkedIdentity> verify(Credentials c) {
    Objects.requireNonNull(c, "c");
    if (open()) {
      return Optional.empty();
    }
    try {
      Optional<LinkedIdentity> identity = delegate.verify(c);
      succeeded();
      return identity;
    } catch (RuntimeException failure) {
      failed();
      throw failure;
    }
  }

  /** The current state of the circuit; for the administration screen and the tests. */
  public synchronized CircuitState state() {
    if (state == CircuitState.OPEN && openedAt.plus(retryWindow).isBefore(clock.instant())) {
      state = CircuitState.HALF_OPEN;
    }
    return state;
  }

  /** Whether the provider must be skipped right now. */
  public synchronized boolean open() {
    state();
    return state == CircuitState.OPEN;
  }

  private synchronized void succeeded() {
    failures = 0;
    state = CircuitState.CLOSED;
    openedAt = null;
  }

  private synchronized void failed() {
    failures++;
    if (state == CircuitState.HALF_OPEN || failures >= threshold) {
      state = CircuitState.OPEN;
      openedAt = clock.instant();
    }
  }
}
