package tfb.status.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.io.Serial;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.LogTester;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link TaskScheduler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class TaskSchedulerTest {
  /**
   * Verifies that {@link TaskScheduler#submit(Runnable)} works as expected when
   * the task is allowed to complete.
   */
  @Test
  public void testSubmit_runnable_completed(TaskScheduler taskScheduler)
      throws InterruptedException, TimeoutException, ExecutionException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration taskSleepTime = Duration.ofMillis(50);

    Runnable task =
        () -> {
          counter.incrementAndGet();
          try {
            Thread.sleep(taskSleepTime.toMillis());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          counter.incrementAndGet();
        };

    ListenableFuture<?> future = taskScheduler.submit(task);

    Thread.sleep(taskSleepTime.dividedBy(2).toMillis());
    assertEquals(1, counter.get());
    Thread.sleep(taskSleepTime.toMillis());
    assertEquals(2, counter.get());
    Thread.sleep(taskSleepTime.toMillis());
    assertEquals(2, counter.get());
    assertFalse(future.cancel(true));
    assertNull(future.get(0, TimeUnit.MILLISECONDS));
  }

  /**
   * Verifies that {@link TaskScheduler#submit(Callable)} works as expected when
   * the task is allowed to complete.
   */
  @Test
  public void testSubmit_callable_completed(TaskScheduler taskScheduler)
      throws InterruptedException, TimeoutException, ExecutionException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration taskSleepTime = Duration.ofMillis(50);
    String result = "hi";

    Callable<String> task =
        () -> {
          counter.incrementAndGet();
          try {
            Thread.sleep(taskSleepTime.toMillis());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          counter.incrementAndGet();
          return result;
        };

    ListenableFuture<String> future = taskScheduler.submit(task);

    Thread.sleep(taskSleepTime.dividedBy(2).toMillis());
    assertEquals(1, counter.get());
    Thread.sleep(taskSleepTime.toMillis());
    assertEquals(2, counter.get());
    Thread.sleep(taskSleepTime.toMillis());
    assertEquals(2, counter.get());
    assertFalse(future.cancel(true));
    assertEquals(result, future.get(0, TimeUnit.MILLISECONDS));
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

    Runnable task =
        () -> {
          try {
            counter.incrementAndGet();
            Thread.sleep(taskSleepTime.toMillis());
            counter.incrementAndGet();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        };

    ListenableFuture<?> future = taskScheduler.submit(task);

    Thread.sleep(taskSleepTime.dividedBy(2).toMillis());
    assertEquals(1, counter.get());
    assertTrue(future.cancel(true));
    Thread.sleep(taskSleepTime.toMillis());
    assertEquals(1, counter.get());
    Thread.sleep(taskSleepTime.toMillis());
    assertEquals(1, counter.get());
    assertThrows(
        CancellationException.class,
        () -> future.get(0, TimeUnit.MILLISECONDS));
  }

  /**
   * Verifies that {@link TaskScheduler#submit(Callable)} works as expected when
   * the task is cancelled during execution.
   */
  @Test
  public void testSubmit_callable_cancelledDuringExecution(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration taskSleepTime = Duration.ofMillis(50);
    String result = "hi";

    Callable<String> task =
        () -> {
          try {
            counter.incrementAndGet();
            Thread.sleep(taskSleepTime.toMillis());
            counter.incrementAndGet();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return result;
        };

    ListenableFuture<String> future = taskScheduler.submit(task);

    Thread.sleep(taskSleepTime.dividedBy(2).toMillis());
    assertEquals(1, counter.get());
    assertTrue(future.cancel(true));
    Thread.sleep(taskSleepTime.toMillis());
    assertEquals(1, counter.get());
    Thread.sleep(taskSleepTime.toMillis());
    assertEquals(1, counter.get());
    assertThrows(
        CancellationException.class,
        () -> future.get(0, TimeUnit.MILLISECONDS));
  }

  /**
   * Verifies that {@link TaskScheduler#submit(Runnable)} logs uncaught
   * exceptions.
   */
  @Test
  public void testSubmit_runnable_uncaughtException(TaskScheduler taskScheduler,
                                                    LogTester logs)
      throws InterruptedException {

    String message = "test exception " + UUID.randomUUID();

    Runnable task =
        () -> {
          throw new TestUncheckedException(message);
        };

    ListenableFuture<?> future = taskScheduler.submit(task);

    Thread.sleep(50);
    assertFalse(future.cancel(true));

    ExecutionException thrown =
        assertThrows(
            ExecutionException.class,
            () -> future.get(0, TimeUnit.MILLISECONDS));

    Throwable cause = thrown.getCause();
    assertNotNull(cause);
    assertEquals(message, cause.getMessage());
    assertEquals(TestUncheckedException.class, cause.getClass());

    assertEquals(
        1,
        logs.getEvents()
            .filter(
                event ->
                    logs.isExceptionEvent(
                        /* event= */ event,
                        /* exceptionType= */ TestUncheckedException.class,
                        /* exceptionMessage= */ message))
            .count());
  }

  /**
   * Verifies that {@link TaskScheduler#submit(Callable)} logs uncaught
   * exceptions.
   */
  @Test
  public void testSubmit_callable_uncaughtException(TaskScheduler taskScheduler,
                                                    LogTester logs)
      throws InterruptedException {

    String message = "test exception " + UUID.randomUUID();

    Callable<String> task =
        () -> {
          throw new TestCheckedException(message);
        };

    ListenableFuture<String> future = taskScheduler.submit(task);

    Thread.sleep(50);
    assertFalse(future.cancel(true));

    ExecutionException thrown =
        assertThrows(
            ExecutionException.class,
            () -> future.get(0, TimeUnit.MILLISECONDS));

    Throwable cause = thrown.getCause();
    assertNotNull(cause);
    assertEquals(message, cause.getMessage());
    assertEquals(TestCheckedException.class, cause.getClass());

    assertEquals(
        1,
        logs.getEvents()
            .filter(
                event ->
                    logs.isExceptionEvent(
                        /* event= */ event,
                        /* exceptionType= */ TestCheckedException.class,
                        /* exceptionMessage= */ message))
            .count());
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

    Runnable task =
        () -> {
          counter.incrementAndGet();
        };

    ListenableFuture<?> future = taskScheduler.schedule(task, delay);

    Thread.sleep(delay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    Thread.sleep(delay.toMillis());
    assertEquals(1, counter.get());
    Thread.sleep(delay.toMillis());
    assertEquals(1, counter.get());
    assertFalse(future.cancel(true));
    assertNull(future.get(0, TimeUnit.MILLISECONDS));
  }

  /**
   * Verifies that {@link TaskScheduler#schedule(Callable, Duration)} works as
   * expected when the task is allowed to complete.
   */
  @Test
  public void testSchedule_callable_completed(TaskScheduler taskScheduler)
      throws InterruptedException, TimeoutException, ExecutionException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration delay = Duration.ofMillis(50);
    String result = "hi";

    Callable<String> task =
        () -> {
          counter.incrementAndGet();
          return result;
        };

    ListenableFuture<String> future = taskScheduler.schedule(task, delay);

    Thread.sleep(delay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    Thread.sleep(delay.toMillis());
    assertEquals(1, counter.get());
    Thread.sleep(delay.toMillis());
    assertEquals(1, counter.get());
    assertFalse(future.cancel(true));
    assertEquals(result, future.get(0, TimeUnit.MILLISECONDS));
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

    Runnable task =
        () -> {
          try {
            counter.incrementAndGet();
            Thread.sleep(delay.toMillis());
            counter.incrementAndGet();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        };

    ListenableFuture<?> future = taskScheduler.schedule(task, delay);

    assertEquals(0, counter.get());
    Thread.sleep(delay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    assertTrue(future.cancel(true));
    Thread.sleep(delay.toMillis());
    assertEquals(0, counter.get());
    assertThrows(
        CancellationException.class,
        () -> future.get(0, TimeUnit.MILLISECONDS));
  }

  /**
   * Verifies that {@link TaskScheduler#schedule(Callable, Duration)} works as
   * expected when the task is cancelled before execution.
   */
  @Test
  public void testSchedule_callable_cancelledBeforeExecution(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration delay = Duration.ofMillis(50);
    String result = "hi";

    Callable<String> task =
        () -> {
          try {
            counter.incrementAndGet();
            Thread.sleep(delay.toMillis());
            counter.incrementAndGet();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return result;
        };

    ListenableFuture<String> future = taskScheduler.schedule(task, delay);

    assertEquals(0, counter.get());
    Thread.sleep(delay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    assertTrue(future.cancel(true));
    Thread.sleep(delay.toMillis());
    assertEquals(0, counter.get());
    assertThrows(
        CancellationException.class,
        () -> future.get(0, TimeUnit.MILLISECONDS));
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

    Runnable task =
        () -> {
          try {
            counter.incrementAndGet();
            Thread.sleep(delay.toMillis());
            counter.incrementAndGet();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        };

    ListenableFuture<?> future = taskScheduler.schedule(task, delay);

    assertEquals(0, counter.get());
    Thread.sleep(delay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    Thread.sleep(delay.toMillis());
    assertEquals(1, counter.get());
    assertTrue(future.cancel(true));
    Thread.sleep(delay.toMillis());
    assertEquals(1, counter.get());
    assertThrows(
        CancellationException.class,
        () -> future.get(0, TimeUnit.MILLISECONDS));
  }

  /**
   * Verifies that {@link TaskScheduler#schedule(Callable, Duration)} works as
   * expected when the task is cancelled during execution.
   */
  @Test
  public void testSchedule_callable_cancelledDuringExecution(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration delay = Duration.ofMillis(50);
    String result = "hi";

    Callable<String> task =
        () -> {
          try {
            counter.incrementAndGet();
            Thread.sleep(delay.toMillis());
            counter.incrementAndGet();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return result;
        };

    ListenableFuture<String> future = taskScheduler.schedule(task, delay);

    assertEquals(0, counter.get());
    Thread.sleep(delay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    Thread.sleep(delay.toMillis());
    assertEquals(1, counter.get());
    assertTrue(future.cancel(true));
    Thread.sleep(delay.toMillis());
    assertEquals(1, counter.get());
    assertThrows(
        CancellationException.class,
        () -> future.get(0, TimeUnit.MILLISECONDS));
  }

  /**
   * Verifies that {@link TaskScheduler#schedule(Runnable, Duration)} logs
   * uncaught exceptions.
   */
  @Test
  public void testSchedule_runnable_uncaughtException(TaskScheduler taskScheduler,
                                                      LogTester logs)
      throws InterruptedException {

    String message = "test exception " + UUID.randomUUID();
    Duration delay = Duration.ofMillis(50);

    Runnable task =
        () -> {
          throw new TestUncheckedException(message);
        };

    ListenableFuture<?> future = taskScheduler.schedule(task, delay);

    Thread.sleep(delay.multipliedBy(2).toMillis());
    assertFalse(future.cancel(true));

    ExecutionException thrown =
        assertThrows(
            ExecutionException.class,
            () -> future.get(0, TimeUnit.MILLISECONDS));

    Throwable cause = thrown.getCause();
    assertNotNull(cause);
    assertEquals(message, cause.getMessage());
    assertEquals(TestUncheckedException.class, cause.getClass());

    assertEquals(
        1,
        logs.getEvents()
            .filter(
                event ->
                    logs.isExceptionEvent(
                        /* event= */ event,
                        /* exceptionType= */ TestUncheckedException.class,
                        /* exceptionMessage= */ message))
            .count());
  }

  /**
   * Verifies that {@link TaskScheduler#schedule(Callable, Duration)} logs
   * uncaught exceptions.
   */
  @Test
  public void testSchedule_callable_uncaughtException(TaskScheduler taskScheduler,
                                                      LogTester logs)
      throws InterruptedException {

    String message = "test exception " + UUID.randomUUID();
    Duration delay = Duration.ofMillis(50);

    Callable<String> task =
        () -> {
          throw new TestCheckedException(message);
        };

    ListenableFuture<String> future = taskScheduler.schedule(task, delay);

    Thread.sleep(delay.multipliedBy(2).toMillis());
    assertFalse(future.cancel(true));

    ExecutionException thrown =
        assertThrows(
            ExecutionException.class,
            () -> future.get(0, TimeUnit.MILLISECONDS));

    Throwable cause = thrown.getCause();
    assertNotNull(cause);
    assertEquals(message, cause.getMessage());
    assertEquals(TestCheckedException.class, cause.getClass());

    assertEquals(
        1,
        logs.getEvents()
            .filter(
                event ->
                    logs.isExceptionEvent(
                        /* event= */ event,
                        /* exceptionType= */ TestCheckedException.class,
                        /* exceptionMessage= */ message))
            .count());
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

    Runnable task =
        () -> {
          try {
            counter.incrementAndGet();
            Thread.sleep(interval.toMillis());
            counter.incrementAndGet();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        };

    ListenableFuture<?> future =
        taskScheduler.repeat(task, initialDelay, interval);

    assertEquals(0, counter.get());
    Thread.sleep(initialDelay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    assertTrue(future.cancel(true));
    Thread.sleep(initialDelay.toMillis());
    assertEquals(0, counter.get());
    assertThrows(
        CancellationException.class,
        () -> future.get(0, TimeUnit.MILLISECONDS));
  }

  /**
   * Verifies that {@link TaskScheduler#repeat(Callable, Duration, Duration)}
   * works as expected when the task is cancelled before the first execution.
   */
  @Test
  public void testRepeat_callable_cancelledBeforeFirstExecution(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration initialDelay = Duration.ofMillis(50);
    Duration interval = Duration.ofMillis(50);
    String result = "hi";

    Callable<String> task =
        () -> {
          try {
            counter.incrementAndGet();
            Thread.sleep(interval.toMillis());
            counter.incrementAndGet();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return result;
        };

    ListenableFuture<?> future =
        taskScheduler.repeat(task, initialDelay, interval);

    assertEquals(0, counter.get());
    Thread.sleep(initialDelay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    assertTrue(future.cancel(true));
    Thread.sleep(initialDelay.toMillis());
    assertEquals(0, counter.get());
    assertThrows(
        CancellationException.class,
        () -> future.get(0, TimeUnit.MILLISECONDS));
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

    Runnable task =
        () -> {
          try {
            counter.incrementAndGet();
            Thread.sleep(interval.toMillis());
            counter.incrementAndGet();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        };

    ListenableFuture<?> future =
        taskScheduler.repeat(task, initialDelay, interval);

    assertEquals(0, counter.get());
    Thread.sleep(initialDelay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    Thread.sleep(initialDelay.toMillis());
    assertEquals(1, counter.get());
    Thread.sleep(interval.toMillis());
    assertEquals(2, counter.get());
    assertTrue(future.cancel(true));
    Thread.sleep(interval.toMillis());
    assertEquals(2, counter.get());
    assertThrows(
        CancellationException.class,
        () -> future.get(0, TimeUnit.MILLISECONDS));
  }

  /**
   * Verifies that {@link TaskScheduler#repeat(Callable, Duration, Duration)}
   * works as expected when the task is cancelled between two executions.
   */
  @Test
  public void testRepeat_callable_cancelledBetweenExecutions(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration initialDelay = Duration.ofMillis(50);
    Duration interval = Duration.ofMillis(50);
    String result = "hi";

    Callable<String> task =
        () -> {
          try {
            counter.incrementAndGet();
            Thread.sleep(interval.toMillis());
            counter.incrementAndGet();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return result;
        };

    ListenableFuture<?> future =
        taskScheduler.repeat(task, initialDelay, interval);

    assertEquals(0, counter.get());
    Thread.sleep(initialDelay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    Thread.sleep(initialDelay.toMillis());
    assertEquals(1, counter.get());
    Thread.sleep(interval.toMillis());
    assertEquals(2, counter.get());
    assertTrue(future.cancel(true));
    Thread.sleep(interval.toMillis());
    assertEquals(2, counter.get());
    assertThrows(
        CancellationException.class,
        () -> future.get(0, TimeUnit.MILLISECONDS));
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

    Runnable task =
        () -> {
          try {
            counter.incrementAndGet();
            Thread.sleep(interval.toMillis());
            counter.incrementAndGet();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        };

    ListenableFuture<?> future =
        taskScheduler.repeat(task, initialDelay, interval);

    assertEquals(0, counter.get());
    Thread.sleep(initialDelay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    Thread.sleep(initialDelay.toMillis());
    assertEquals(1, counter.get());
    assertTrue(future.cancel(true));
    Thread.sleep(interval.toMillis());
    assertEquals(1, counter.get());
    assertThrows(
        CancellationException.class,
        () -> future.get(0, TimeUnit.MILLISECONDS));
  }

  /**
   * Verifies that {@link TaskScheduler#repeat(Callable, Duration, Duration)}
   * works as expected when the task is cancelled during execution.
   */
  @Test
  public void testRepeat_callable_cancelledDuringExecution(TaskScheduler taskScheduler)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    Duration initialDelay = Duration.ofMillis(50);
    Duration interval = Duration.ofMillis(50);
    String result = "hi";

    Callable<String> task =
        () -> {
          try {
            counter.incrementAndGet();
            Thread.sleep(interval.toMillis());
            counter.incrementAndGet();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return result;
        };

    ListenableFuture<?> future =
        taskScheduler.repeat(task, initialDelay, interval);

    assertEquals(0, counter.get());
    Thread.sleep(initialDelay.dividedBy(2).toMillis());
    assertEquals(0, counter.get());
    Thread.sleep(initialDelay.toMillis());
    assertEquals(1, counter.get());
    assertTrue(future.cancel(true));
    Thread.sleep(interval.toMillis());
    assertEquals(1, counter.get());
    assertThrows(
        CancellationException.class,
        () -> future.get(0, TimeUnit.MILLISECONDS));
  }

  /**
   * Verifies that {@link TaskScheduler#repeat(Runnable, Duration, Duration)}
   * logs uncaught exceptions.
   */
  @Test
  public void testRepeat_runnable_uncaughtException(TaskScheduler taskScheduler,
                                                      LogTester logs)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    String messagePrefix = "test exception " + UUID.randomUUID() + "#";
    Duration initialDelay = Duration.ofMillis(50);
    Duration interval = Duration.ofMillis(50);

    Runnable task =
        () -> {
          throw new TestUncheckedException(
              messagePrefix + counter.incrementAndGet());
        };

    ListenableFuture<?> future =
        taskScheduler.repeat(task, initialDelay, interval);

    // Let the task run at least twice.
    Thread.sleep(initialDelay.plus(interval.multipliedBy(3)).toMillis());

    assertTrue(future.cancel(true));

    assertThrows(
        CancellationException.class,
        () -> future.get(0, TimeUnit.MILLISECONDS));

    assertEquals(
        1,
        logs.getEvents()
            .filter(
                event ->
                    logs.isExceptionEvent(
                        /* event= */ event,
                        /* exceptionType= */ TestUncheckedException.class,
                        /* exceptionMessage= */ messagePrefix + 1))
            .count());

    assertEquals(
        1,
        logs.getEvents()
            .filter(
                event ->
                    logs.isExceptionEvent(
                        /* event= */ event,
                        /* exceptionType= */ TestUncheckedException.class,
                        /* exceptionMessage= */ messagePrefix + 2))
            .count());
  }

  /**
   * Verifies that {@link TaskScheduler#repeat(Callable, Duration, Duration)}
   * logs uncaught exceptions.
   */
  @Test
  public void testRepeat_callable_uncaughtException(TaskScheduler taskScheduler,
                                                    LogTester logs)
      throws InterruptedException {

    AtomicInteger counter = new AtomicInteger(0);
    String messagePrefix = "test exception " + UUID.randomUUID() + "#";
    Duration initialDelay = Duration.ofMillis(50);
    Duration interval = Duration.ofMillis(50);

    Callable<String> task =
        () -> {
          throw new TestCheckedException(
              messagePrefix + counter.incrementAndGet());
        };

    ListenableFuture<?> future =
        taskScheduler.repeat(task, initialDelay, interval);

    // Let the task run at least twice.
    Thread.sleep(initialDelay.plus(interval.multipliedBy(3)).toMillis());

    assertTrue(future.cancel(true));

    assertThrows(
        CancellationException.class,
        () -> future.get(0, TimeUnit.MILLISECONDS));

    assertEquals(
        1,
        logs.getEvents()
            .filter(
                event ->
                    logs.isExceptionEvent(
                        /* event= */ event,
                        /* exceptionType= */ TestCheckedException.class,
                        /* exceptionMessage= */ messagePrefix + 1))
            .count());

    assertEquals(
        1,
        logs.getEvents()
            .filter(
                event ->
                    logs.isExceptionEvent(
                        /* event= */ event,
                        /* exceptionType= */ TestCheckedException.class,
                        /* exceptionMessage= */ messagePrefix + 2))
            .count());
  }

  private static final class TestUncheckedException extends RuntimeException {
    TestUncheckedException(String message) {
      super(Objects.requireNonNull(message));
    }

    @Serial
    private static final long serialVersionUID = 0;
  }

  private static final class TestCheckedException extends IOException {
    TestCheckedException(String message) {
      super(Objects.requireNonNull(message));
    }

    @Serial
    private static final long serialVersionUID = 0;
  }
}
