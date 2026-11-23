package co.omnistream.infrastructure.identity;

/** State of the circuit of a {@link ResilientIdentityProvider} (Circuit Breaker, version 2.0). */
public enum CircuitState {
  /** The provider is called; failures are counted. */
  CLOSED,
  /** The provider is skipped; after the retry window it is tried again. */
  OPEN,
  /** The retry window passed: one call decides whether the circuit closes or opens again. */
  HALF_OPEN
}
