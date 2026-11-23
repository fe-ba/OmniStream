package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.port.out.IUnitOfWork;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * In-memory unit of work for the domain tests (ADR 0018).
 *
 * <p>It runs the work at once and remembers whether a transaction is open, which is what the audit
 * decorators check. It cannot roll back changes already written to the other in-memory ports.
 */
public final class InMemoryUnitOfWork implements IUnitOfWork {

  private int depth;

  @Override
  public <T> T execute(Supplier<T> work) {
    Objects.requireNonNull(work, "work");
    depth++;
    try {
      return work.get();
    } finally {
      depth--;
    }
  }

  /** Whether a work is running on this thread. */
  public boolean inside() {
    return depth > 0;
  }

  /** How many nested works are running, for the assertions of a test. */
  public int depth() {
    return depth;
  }
}
