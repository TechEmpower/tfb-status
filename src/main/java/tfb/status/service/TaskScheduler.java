package tfb.status.service;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs tasks asynchronously.
 *
 * <p>Exceptions thrown from tasks are logged.
 */
@Singleton
public final class TaskScheduler implements PreDestroy {
  private final ListeningScheduledExecutorService scheduler;
  private final ListeningExecutorService executor;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final FutureCallback<Object> logExceptions =
      new FutureCallback<Object>() {
        @Override
        public void onSuccess(@Nullable Object result) {
          // Do nothing.
        }

        @Override
        public void onFailure(Throwable t) {
          if (!(t instanceof CancellationException))
            logger.error("Uncaught exception from task", t);
        }
      };

  public TaskScheduler() {
    var scheduler = new ScheduledThreadPoolExecutor(1);
    scheduler.setRemoveOnCancelPolicy(true);
    scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

    this.scheduler =
        MoreExecutors.listeningDecorator(
            Executors.unconfigurableScheduledExecutorService(scheduler));

    ThreadFactory threadFactory =
        new ThreadFactoryBuilder()
            .setNameFormat("task-scheduler-thread-%s")
            .build();

    var executor =
        new ThreadPoolExecutor(
            /* corePoolSize= */ 0,
            /* maximumPoolSize= */ Integer.MAX_VALUE,
            /* keepAliveTime= */ 60L,
            /* unit= */ TimeUnit.SECONDS,
            /* workQueue= */ new SynchronousQueue<>(),
            /* threadFactory= */ threadFactory);

    this.executor =
        MoreExecutors.listeningDecorator(
            Executors.unconfigurableExecutorService(executor));
  }

  @Override
  public void preDestroy() {
    shutdown();
  }

  /**
   * Shuts down this task scheduler.
   */
  public void shutdown() {
    scheduler.shutdown();
    executor.shutdown();

    try {
      scheduler.awaitTermination(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      if (!scheduler.isTerminated()) {
        scheduler.shutdownNow();
      }
    }

    try {
      executor.awaitTermination(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      if (!executor.isTerminated()) {
        executor.shutdownNow();
      }
    }
  }

  /**
   * Runs the specified task asynchronously as soon as possible.
   *
   * @param task the task to run
   * @return a future that completes when the task does and that may be used to
   *         cancel the task
   * @throws RejectedExecutionException if {@link #shutdown()} was called
   */
  @CanIgnoreReturnValue // failure will be logged, at least
  public ListenableFuture<?> submit(Runnable task) {
    return submit(Executors.callable(task));
  }

  /**
   * Runs the specified task asynchronously as soon as possible.
   *
   * @param task the task to run
   * @return a future that completes with the result of the task and that may be
   *         used to cancel the task
   * @throws RejectedExecutionException if {@link #shutdown()} was called
   */
  public <T> ListenableFuture<T> submit(Callable<T> task) {
    return internalSubmit(Objects.requireNonNull(task));
  }

  private <T> ListenableFuture<T> internalSubmit(Callable<T> task) {
    ListenableFuture<T> future = executor.submit(task);
    Futures.addCallback(future, logExceptions, executor);
    return future;
  }

  /**
   * Runs the specified task asynchronously after a delay.
   *
   * @param task the task to run
   * @param delay the amount of time to wait before running the task
   * @return a future that completes when the task does and that may be used to
   *         cancel the task
   * @throws IllegalArgumentException if {@code delay} is negative
   * @throws RejectedExecutionException if {@link #shutdown()} was called
   */
  public ListenableFuture<?> schedule(Runnable task, Duration delay) {
    return schedule(Executors.callable(task), delay);
  }

  /**
   * Runs the specified task asynchronously after a delay.
   *
   * @param task the task to run
   * @param delay the amount of time to wait before running the task
   * @return a future that completes with the result of the task and that may be
   *         used to cancel the task
   * @throws IllegalArgumentException if {@code delay} is negative
   * @throws RejectedExecutionException if {@link #shutdown()} was called
   */
  public <T> ListenableFuture<T> schedule(Callable<T> task, Duration delay) {
    Objects.requireNonNull(task);
    Objects.requireNonNull(delay);

    if (delay.isNegative())
      throw new IllegalArgumentException(
          "negative delay: " + delay);

    return internalSchedule(task, delay);
  }

  private <T> ListenableFuture<T> internalSchedule(Callable<T> task,
                                                   Duration delay) {
    return Futures.scheduleAsync(
        () -> internalSubmit(task),
        delay,
        scheduler);
  }

  /**
   * Repeatedly runs the specified task asynchronously.
   *
   * <p>The returned future will never complete, successfully or exceptionally,
   * unless it is cancelled.  Exceptions thrown from the task's {@link
   * Runnable#run()} method are logged but not propagated to the returned
   * future, and such exceptions will not prevent the task from repeating.
   *
   * @param task the task to run
   * @param initialDelay the amount of time to wait before running the task
   *        initially
   * @param interval the amount of time to wait after the task finishes running
   *        before running the task again
   * @return a future that may be used to cancel the task
   * @throws IllegalArgumentException if {@code initialDelay} or {@code
   *         interval} is negative
   * @throws RejectedExecutionException if {@link #shutdown()} was called
   */
  public ListenableFuture<?> repeat(Runnable task,
                                    Duration initialDelay,
                                    Duration interval) {
    return repeat(Executors.callable(task), initialDelay, interval);
  }

  /**
   * Repeatedly runs the specified task asynchronously.
   *
   * <p>The returned future will never complete, successfully or exceptionally,
   * unless it is cancelled.  Exceptions thrown from the task's {@link
   * Callable#call()} method are logged but not propagated to the returned
   * future, and such exceptions will not prevent the task from repeating.
   *
   * @param task the task to run
   * @param initialDelay the amount of time to wait before running the task
   *        initially
   * @param interval the amount of time to wait after the task finishes running
   *        before running the task again
   * @return a future that may be used to cancel the task
   * @throws IllegalArgumentException if {@code initialDelay} or {@code
   *         interval} is negative
   * @throws RejectedExecutionException if {@link #shutdown()} was called
   */
  public <T> ListenableFuture<T> repeat(Callable<T> task,
                                        Duration initialDelay,
                                        Duration interval) {
    Objects.requireNonNull(task);
    Objects.requireNonNull(initialDelay);
    Objects.requireNonNull(interval);

    if (initialDelay.isNegative())
      throw new IllegalArgumentException(
          "negative initialDelay: " + initialDelay);

    if (interval.isNegative())
      throw new IllegalArgumentException(
          "negative interval: " + interval);

    return new AbstractFuture<T>() {
      @GuardedBy("this")
      ListenableFuture<T> next = internalSchedule(task, initialDelay);

      final FutureCallback<T> reschedule =
          new FutureCallback<T>() {
            @Override
            public void onSuccess(@Nullable T result) {
              scheduleNext();
            }

            @Override
            public void onFailure(Throwable t) {
              scheduleNext();
            }
          };

      {
        Futures.addCallback(next, reschedule, executor);
      }

      synchronized void scheduleNext() {
        if (!isCancelled()) {
          next = internalSchedule(task, interval);
          Futures.addCallback(next, reschedule, executor);
        }
      }

      @Override
      protected synchronized void afterDone() {
        if (isCancelled())
          next.cancel(wasInterrupted());
      }
    };
  }
}
