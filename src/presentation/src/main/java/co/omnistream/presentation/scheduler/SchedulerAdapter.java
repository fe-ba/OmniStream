package co.omnistream.presentation.scheduler;

import co.omnistream.domain.model.access.UserId;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Primary adapter that runs {@link ScheduledTask}s every so often, as the system identity.
 *
 * <p>Tasks run one at a time, on a single background thread, so a reindex and a backup never touch
 * the catalog at once. Each task runs again {@code every} after the previous run ends. A task that
 * fails is logged and keeps its schedule: one bad run does not stop the next ones.
 *
 * <p>Intervals instead of cron expressions: version 1.0 only needs "every so often", and a cron
 * parser would be one more library.
 */
public final class SchedulerAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(SchedulerAdapter.class);

  private final Map<String, ScheduledTask> tasks = new LinkedHashMap<>();
  private final Map<String, Duration> intervals = new LinkedHashMap<>();
  private final UserId systemIdentity;
  private ScheduledExecutorService executor;

  public SchedulerAdapter(UserId systemIdentity) {
    this.systemIdentity = Objects.requireNonNull(systemIdentity, "systemIdentity");
  }

  /**
   * Adds a task that runs every {@code every}, the first time {@code every} after {@link #start}.
   *
   * @throws IllegalArgumentException if the interval is not positive or a task with the same name
   *     is already registered
   * @throws IllegalStateException if the scheduler has already started
   */
  public synchronized void register(Duration every, ScheduledTask t) {
    Objects.requireNonNull(every, "every");
    Objects.requireNonNull(t, "t");
    if (!every.isPositive()) {
      throw new IllegalArgumentException("A task must run every positive interval");
    }
    if (tasks.containsKey(t.name())) {
      throw new IllegalArgumentException("A task named " + t.name() + " is already registered");
    }
    if (executor != null) {
      throw new IllegalStateException("Register the tasks before starting the scheduler");
    }
    tasks.put(t.name(), t);
    intervals.put(t.name(), every);
  }

  /** Starts running the registered tasks. Starting it again does nothing. */
  public synchronized void start() {
    if (executor != null) {
      return;
    }
    executor =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
          Thread thread = new Thread(runnable, "omnistream-scheduler");
          thread.setDaemon(true);
          return thread;
        });
    tasks.forEach((name, task) -> {
      long millis = intervals.get(name).toMillis();
      executor.scheduleWithFixedDelay(() -> fire(task), millis, millis, TimeUnit.MILLISECONDS);
      LOG.info("Scheduled task {} every {}", name, intervals.get(name));
    });
  }

  /**
   * Stops the tasks: none starts after this, and a task that is running gets a few seconds to
   * finish.
   */
  public synchronized void stop() {
    if (executor == null) {
      return;
    }
    executor.shutdown();
    try {
      if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private void fire(ScheduledTask t) {
    long start = System.nanoTime();
    try {
      t.execute(systemIdentity);
      LOG.debug("Task {} finished in {} ms", t.name(), (System.nanoTime() - start) / 1_000_000);
    } catch (RuntimeException e) {
      // Thrown out of here, the exception would cancel every future run of the task
      LOG.error("Task {} failed", t.name(), e);
    }
  }
}
