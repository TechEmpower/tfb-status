package tfb.status.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.service.TaskScheduler.CancellableTask;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link TaskScheduler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class TaskSchedulerTest {
  /**
   * Verifies that {@link TaskScheduler#submit(TaskScheduler.RunnableTask)}
   * works as expected when the task is allowed to complete.
   */
  @Test
  public void testSubmit_completed(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration taskSleepTime = Duration.ofMillis(50);

    CancellableTask task =
        taskScheduler.submit(
            () -> {
              counter.incrementAndGet();
              Thread.sleep(taskSleepTime.toMillis());
              counter.incrementAndGet();
            });

    Thread.sleep(taskSleepTime.dividedBy(2).toMillis());
    assertEquals(1, counter.get());
    Thread.sleep(taskSleepTime.toMillis());
    assertEquals(2, counter.get());
    Thread.sleep(taskSleepTime.toMillis());
    assertEquals(2, counter.get());
    task.cancel(); // should do nothing and not throw
  }

  /**
   * Verifies that {@link TaskScheduler#submit(TaskScheduler.RunnableTask)}
   * works as expected when the task is cancelled during execution.
   */
  @Test
  public void testSubmit_cancelledDuringExecution(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    AtomicBoolean interrupted = new AtomicBoolean(false);
    Duration taskSleepTime = Duration.ofMillis(50);

    CancellableTask task =
        taskScheduler.submit(
            () -> {
              try {
                counter.incrementAndGet();
                Thread.sleep(taskSleepTime.toMillis());
                counter.incrementAndGet();
              } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
              }
            });

    Thread.sleep(taskSleepTime.dividedBy(2).toMillis());
    assertEquals(1, counter.get());
    task.cancel();
    Thread.sleep(taskSleepTime.toMillis());
    assertEquals(1, counter.get());
    Thread.sleep(taskSleepTime.toMillis());
    assertEquals(1, counter.get());
  }

  /**
   * Verifies that {@link TaskScheduler#schedule(TaskScheduler.RunnableTask,
   * Duration)} works as expected when the task is allowed to complete.
   */
  @Test
  public void testSchedule_completed(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration delay = Duration.ofMillis(50);

    CancellableTask task =
        taskScheduler.schedule(
            () -> {
              counter.incrementAndGet();
            },
            delay);

    Thread.sleep(delay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    Thread.sleep(delay.toMillis());
    assertEquals(1, counter.get());
    Thread.sleep(delay.toMillis());
    assertEquals(1, counter.get());
    task.cancel(); // should do nothing and not throw
  }

  /**
   * Verifies that {@link TaskScheduler#schedule(TaskScheduler.RunnableTask,
   * Duration)} works as expected when the task is cancelled before execution.
   */
  @Test
  public void testSchedule_cancelledBeforeExecution(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    AtomicBoolean interrupted = new AtomicBoolean(false);
    Duration delay = Duration.ofMillis(50);

    CancellableTask task =
        taskScheduler.schedule(
            () -> {
              try {
                counter.incrementAndGet();
                Thread.sleep(delay.toMillis());
                counter.incrementAndGet();
              } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
              }
            },
            delay);

    assertEquals(0, counter.get());
    Thread.sleep(delay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    task.cancel();
    Thread.sleep(delay.toMillis());
    assertEquals(0, counter.get());
    assertFalse(interrupted.get());
  }

  /**
   * Verifies that {@link TaskScheduler#schedule(TaskScheduler.RunnableTask,
   * Duration)} works as expected when the task is cancelled during execution.
   */
  @Test
  public void testSchedule_cancelledDuringExecution(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    AtomicBoolean interrupted = new AtomicBoolean(false);
    Duration delay = Duration.ofMillis(50);

    CancellableTask task =
        taskScheduler.schedule(
            () -> {
              try {
                counter.incrementAndGet();
                Thread.sleep(delay.toMillis());
                counter.incrementAndGet();
              } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
              }
            },
            delay);

    assertEquals(0, counter.get());
    Thread.sleep(delay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    Thread.sleep(delay.toMillis());
    assertEquals(1, counter.get());
    task.cancel();
    Thread.sleep(delay.toMillis());
    assertEquals(1, counter.get());
    assertTrue(interrupted.get());
  }

  /**
   * Verifies that {@link TaskScheduler#repeat(TaskScheduler.RunnableTask,
   * Duration, Duration)} works as expected when the task is cancelled before
   * the first execution.
   */
  @Test
  public void testRepeat_cancelledBeforeFirstExecution(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    AtomicBoolean interrupted = new AtomicBoolean(false);
    Duration initialDelay = Duration.ofMillis(50);
    Duration interval = Duration.ofMillis(50);

    CancellableTask task =
        taskScheduler.repeat(
            () -> {
              try {
                counter.incrementAndGet();
                Thread.sleep(interval.toMillis());
                counter.incrementAndGet();
              } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
              }
            },
            initialDelay,
            interval);

    assertEquals(0, counter.get());
    Thread.sleep(initialDelay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    task.cancel();
    Thread.sleep(initialDelay.toMillis());
    assertEquals(0, counter.get());
    assertFalse(interrupted.get());
  }

  /**
   * Verifies that {@link TaskScheduler#repeat(TaskScheduler.RunnableTask,
   * Duration, Duration)} works as expected when the task is cancelled between
   * two executions.
   */
  @Test
  public void testRepeat_cancelledBetweenExecutions(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    AtomicBoolean interrupted = new AtomicBoolean(false);
    Duration initialDelay = Duration.ofMillis(50);
    Duration interval = Duration.ofMillis(50);

    CancellableTask task =
        taskScheduler.repeat(
            () -> {
              try {
                counter.incrementAndGet();
                Thread.sleep(interval.toMillis());
                counter.incrementAndGet();
              } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
              }
            },
            initialDelay,
            interval);

    assertEquals(0, counter.get());
    Thread.sleep(initialDelay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    Thread.sleep(initialDelay.toMillis());
    assertEquals(1, counter.get());
    Thread.sleep(interval.toMillis());
    assertEquals(2, counter.get());
    task.cancel();
    Thread.sleep(interval.toMillis());
    assertEquals(2, counter.get());
    assertFalse(interrupted.get());
  }

  /**
   * Verifies that {@link TaskScheduler#repeat(TaskScheduler.RunnableTask,
   * Duration, Duration)} works as expected when the task is cancelled during
   * execution.
   */
  @Test
  public void testRepeat_cancelledDuringExecution(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    AtomicBoolean interrupted = new AtomicBoolean(false);
    Duration initialDelay = Duration.ofMillis(50);
    Duration interval = Duration.ofMillis(50);

    CancellableTask task =
        taskScheduler.repeat(
            () -> {
              try {
                counter.incrementAndGet();
                Thread.sleep(interval.toMillis());
                counter.incrementAndGet();
              } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
              }
            },
            initialDelay,
            interval);

    assertEquals(0, counter.get());
    Thread.sleep(initialDelay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    Thread.sleep(initialDelay.toMillis());
    assertEquals(1, counter.get());
    task.cancel();
    Thread.sleep(interval.toMillis());
    assertEquals(1, counter.get());
    assertTrue(interrupted.get());
  }
}
