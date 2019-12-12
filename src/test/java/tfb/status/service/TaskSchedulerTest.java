package tfb.status.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.util.concurrent.ListenableFuture;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link TaskScheduler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class TaskSchedulerTest {
  // TODO: Test the methods that accept Callable.

  /**
   * Verifies that {@link TaskScheduler#submit(Runnable)} works as expected when
   * the task is allowed to complete.
   */
  @Test
  public void testSubmit_runnable_completed(TaskScheduler taskScheduler)
      throws InterruptedException, TimeoutException, ExecutionException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration taskSleepTime = Duration.ofMillis(50);

    ListenableFuture<?> task =
        taskScheduler.submit(
            () -> {
              counter.incrementAndGet();
              try {
                Thread.sleep(taskSleepTime.toMillis());
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              counter.incrementAndGet();
            });

    Thread.sleep(taskSleepTime.dividedBy(2).toMillis());
    assertEquals(1, counter.get());
    Thread.sleep(taskSleepTime.toMillis());
    assertEquals(2, counter.get());
    Thread.sleep(taskSleepTime.toMillis());
    assertEquals(2, counter.get());
    assertFalse(task.cancel(true));
    assertNull(task.get(0, TimeUnit.MILLISECONDS));
  }

  /**
   * Verifies that {@link TaskScheduler#submit(Runnable)} works as expected when
   * the task is cancelled during execution.
   */
  @Test
  public void testSubmit_runnable_cancelledDuringExecution(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration taskSleepTime = Duration.ofMillis(50);

    ListenableFuture<?> task =
        taskScheduler.submit(
            () -> {
              try {
                counter.incrementAndGet();
                Thread.sleep(taskSleepTime.toMillis());
                counter.incrementAndGet();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    Thread.sleep(taskSleepTime.dividedBy(2).toMillis());
    assertEquals(1, counter.get());
    assertTrue(task.cancel(true));
    Thread.sleep(taskSleepTime.toMillis());
    assertEquals(1, counter.get());
    Thread.sleep(taskSleepTime.toMillis());
    assertEquals(1, counter.get());
    assertThrows(
        CancellationException.class,
        () -> task.get(0, TimeUnit.MILLISECONDS));
  }

  /**
   * Verifies that {@link TaskScheduler#schedule(Runnable, Duration)} works as
   * expected when the task is allowed to complete.
   */
  @Test
  public void testSchedule_runnable_completed(TaskScheduler taskScheduler)
      throws InterruptedException, TimeoutException, ExecutionException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration delay = Duration.ofMillis(50);

    ListenableFuture<?> task =
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
    assertFalse(task.cancel(true));
    assertNull(task.get(0, TimeUnit.MILLISECONDS));
  }

  /**
   * Verifies that {@link TaskScheduler#schedule(Runnable, Duration)} works as
   * expected when the task is cancelled before execution.
   */
  @Test
  public void testSchedule_runnable_cancelledBeforeExecution(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration delay = Duration.ofMillis(50);

    ListenableFuture<?> task =
        taskScheduler.schedule(
            () -> {
              try {
                counter.incrementAndGet();
                Thread.sleep(delay.toMillis());
                counter.incrementAndGet();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            },
            delay);

    assertEquals(0, counter.get());
    Thread.sleep(delay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    assertTrue(task.cancel(true));
    Thread.sleep(delay.toMillis());
    assertEquals(0, counter.get());
    assertThrows(
        CancellationException.class,
        () -> task.get(0, TimeUnit.MILLISECONDS));
  }

  /**
   * Verifies that {@link TaskScheduler#schedule(Runnable, Duration)} works as
   * expected when the task is cancelled during execution.
   */
  @Test
  public void testSchedule_runnable_cancelledDuringExecution(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration delay = Duration.ofMillis(50);

    ListenableFuture<?> task =
        taskScheduler.schedule(
            () -> {
              try {
                counter.incrementAndGet();
                Thread.sleep(delay.toMillis());
                counter.incrementAndGet();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            },
            delay);

    assertEquals(0, counter.get());
    Thread.sleep(delay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    Thread.sleep(delay.toMillis());
    assertEquals(1, counter.get());
    assertTrue(task.cancel(true));
    Thread.sleep(delay.toMillis());
    assertEquals(1, counter.get());
    assertThrows(
        CancellationException.class,
        () -> task.get(0, TimeUnit.MILLISECONDS));
  }

  /**
   * Verifies that {@link TaskScheduler#repeat(Runnable, Duration, Duration)}
   * works as expected when the task is cancelled before the first execution.
   */
  @Test
  public void testRepeat_runnable_cancelledBeforeFirstExecution(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration initialDelay = Duration.ofMillis(50);
    Duration interval = Duration.ofMillis(50);

    ListenableFuture<?> task =
        taskScheduler.repeat(
            () -> {
              try {
                counter.incrementAndGet();
                Thread.sleep(interval.toMillis());
                counter.incrementAndGet();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            },
            initialDelay,
            interval);

    assertEquals(0, counter.get());
    Thread.sleep(initialDelay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    assertTrue(task.cancel(true));
    Thread.sleep(initialDelay.toMillis());
    assertEquals(0, counter.get());
    assertThrows(
        CancellationException.class,
        () -> task.get(0, TimeUnit.MILLISECONDS));
  }

  /**
   * Verifies that {@link TaskScheduler#repeat(Runnable, Duration, Duration)}
   * works as expected when the task is cancelled between two executions.
   */
  @Test
  public void testRepeat_runnable_cancelledBetweenExecutions(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration initialDelay = Duration.ofMillis(50);
    Duration interval = Duration.ofMillis(50);

    ListenableFuture<?> task =
        taskScheduler.repeat(
            () -> {
              try {
                counter.incrementAndGet();
                Thread.sleep(interval.toMillis());
                counter.incrementAndGet();
              } catch (InterruptedException e) {
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
    assertTrue(task.cancel(true));
    Thread.sleep(interval.toMillis());
    assertEquals(2, counter.get());
    assertThrows(
        CancellationException.class,
        () -> task.get(0, TimeUnit.MILLISECONDS));
  }

  /**
   * Verifies that {@link TaskScheduler#repeat(Runnable, Duration, Duration)}
   * works as expected when the task is cancelled during execution.
   */
  @Test
  public void testRepeat_runnable_cancelledDuringExecution(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration initialDelay = Duration.ofMillis(50);
    Duration interval = Duration.ofMillis(50);

    ListenableFuture<?> task =
        taskScheduler.repeat(
            () -> {
              try {
                counter.incrementAndGet();
                Thread.sleep(interval.toMillis());
                counter.incrementAndGet();
              } catch (InterruptedException e) {
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
    assertTrue(task.cancel(true));
    Thread.sleep(interval.toMillis());
    assertEquals(1, counter.get());
    assertThrows(
        CancellationException.class,
        () -> task.get(0, TimeUnit.MILLISECONDS));
  }
}
