package co.omnistream.presentation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.model.access.UserId;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SchedulerAdapterTest {

  private static final UserId SYSTEM = new UserId(UUID.randomUUID());
  private static final Duration OFTEN = Duration.ofMillis(10);

  private final SchedulerAdapter scheduler = new SchedulerAdapter(SYSTEM);

  /** A task that counts its runs and can be told to fail. */
  private static final class CountingTask implements ScheduledTask {
    private final String name;
    private final CountDownLatch runs;
    private final boolean failFirst;
    final List<UserId> executors = new CopyOnWriteArrayList<>();
    final AtomicInteger count = new AtomicInteger();

    CountingTask(String name, int expectedRuns, boolean failFirst) {
      this.name = name;
      this.runs = new CountDownLatch(expectedRuns);
      this.failFirst = failFirst;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public void execute(UserId executor) {
      executors.add(executor);
      int run = count.incrementAndGet();
      runs.countDown();
      if (failFirst && run == 1) {
        throw new IllegalStateException("first run fails");
      }
    }

    boolean awaitRuns() throws InterruptedException {
      return runs.await(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void aTaskRunsAgainAndAgainAsTheSystemIdentity() throws InterruptedException {
    CountingTask task = new CountingTask("t", 3, false);
    scheduler.register(OFTEN, task);

    scheduler.start();
    try {
      assertThat(task.awaitRuns()).isTrue();
    } finally {
      scheduler.stop();
    }
    assertThat(task.executors).containsOnly(SYSTEM);
  }

  @Test
  void aFailingRunDoesNotCancelTheNextOnes() throws InterruptedException {
    CountingTask task = new CountingTask("t", 3, true);
    scheduler.register(OFTEN, task);

    scheduler.start();
    try {
      assertThat(task.awaitRuns()).isTrue();
    } finally {
      scheduler.stop();
    }
  }

  @Test
  void afterStoppingNoTaskRuns() throws InterruptedException {
    CountingTask task = new CountingTask("t", 2, false);
    scheduler.register(OFTEN, task);
    scheduler.start();
    assertThat(task.awaitRuns()).isTrue();

    scheduler.stop();
    int afterStop = task.count.get();
    Thread.sleep(100);

    assertThat(task.count.get()).isEqualTo(afterStop);
  }

  @Test
  void tasksRunOneAtATime() throws InterruptedException {
    AtomicInteger running = new AtomicInteger();
    AtomicInteger mostAtOnce = new AtomicInteger();
    CountDownLatch runs = new CountDownLatch(10);
    for (String name : List.of("a", "b", "c")) {
      scheduler.register(
          OFTEN,
          new ScheduledTask() {
            @Override
            public String name() {
              return name;
            }

            @Override
            public void execute(UserId executor) {
              mostAtOnce.accumulateAndGet(running.incrementAndGet(), Math::max);
              try {
                Thread.sleep(5);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              running.decrementAndGet();
              runs.countDown();
            }
          });
    }

    scheduler.start();
    try {
      assertThat(runs.await(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      scheduler.stop();
    }
    assertThat(mostAtOnce.get()).isEqualTo(1);
  }

  @Test
  void registrationRules() {
    scheduler.register(OFTEN, new CountingTask("t", 1, false));

    assertThatThrownBy(() -> scheduler.register(OFTEN, new CountingTask("t", 1, false)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> scheduler.register(Duration.ZERO, new CountingTask("u", 1, false)))
        .isInstanceOf(IllegalArgumentException.class);

    scheduler.start();
    try {
      assertThatThrownBy(() -> scheduler.register(OFTEN, new CountingTask("v", 1, false)))
          .isInstanceOf(IllegalStateException.class);
    } finally {
      scheduler.stop();
    }
  }
}
