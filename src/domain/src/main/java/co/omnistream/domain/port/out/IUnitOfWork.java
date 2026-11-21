package co.omnistream.domain.port.out;

import java.util.function.Supplier;

/** Driven port: defined by the domain, implemented by secondary adapters. */
public interface IUnitOfWork {
  /**
   * Runs {@code work} in one transaction: everything it saves is kept if it returns, and nothing if
   * it throws. A call made while another one is running on the same thread joins it, so the audit
   * decorators (ADR 0025) can wrap a service that uses a unit of work itself, and the change and
   * its audit entry are saved together.
   */
  <T> T execute(Supplier<T> work);
}
