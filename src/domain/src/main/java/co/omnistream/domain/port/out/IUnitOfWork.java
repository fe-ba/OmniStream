package co.omnistream.domain.port.out;

import java.util.function.Supplier;

/** Driven port: defined by the domain, implemented by secondary adapters. */
public interface IUnitOfWork {
  <T> T execute(Supplier<T> work);
}
