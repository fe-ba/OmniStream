package co.omnistream.presentation.scheduler;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.port.in.IEventDispatch;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes the domain events waiting in the outbox (Transactional Outbox pattern), at most {@code
 * limit} per run.
 */
public final class SyncTask implements ScheduledTask {

  private static final Logger LOG = LoggerFactory.getLogger(SyncTask.class);

  private final IEventDispatch eventDispatch;
  private final int limit;

  public SyncTask(IEventDispatch eventDispatch, int limit) {
    this.eventDispatch = Objects.requireNonNull(eventDispatch, "eventDispatch");
    if (limit <= 0) {
      throw new IllegalArgumentException("The limit must be positive");
    }
    this.limit = limit;
  }

  @Override
  public String name() {
    return "sync";
  }

  /** Publishing events needs no user; {@code executor} is ignored. */
  @Override
  public void execute(UserId executor) {
    int published = eventDispatch.publishPending(limit);
    if (published > 0) {
      LOG.debug("Published {} pending events", published);
    }
  }
}
