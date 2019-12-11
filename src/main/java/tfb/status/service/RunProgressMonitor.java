package tfb.status.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.MessagingException;
import org.glassfish.hk2.api.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.RunProgressMonitorConfig;
import tfb.status.service.TaskScheduler.CancellableTask;

/**
 * Complains over email when a benchmarking environment has stopped sending
 * updates.
 */
@Singleton
public final class RunProgressMonitor implements PreDestroy {
  private final RunProgressMonitorConfig config;
  private final EmailSender emailSender;
  private final TaskScheduler taskScheduler;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @GuardedBy("this")
  private final Map<String, CancellableTask> environmentToTask = new HashMap<>();

  @Inject
  public RunProgressMonitor(RunProgressMonitorConfig config,
                            EmailSender emailSender,
                            TaskScheduler taskScheduler) {

    this.config = Objects.requireNonNull(config);
    this.emailSender = Objects.requireNonNull(emailSender);
    this.taskScheduler = Objects.requireNonNull(taskScheduler);
  }

  @Override
  public void preDestroy() {
    stop();
  }

  /**
   * Cleans up resources used by this service.
   */
  public synchronized void stop() {
    for (CancellableTask task : environmentToTask.values())
      task.cancel();

    environmentToTask.clear();
  }

  /**
   * Notifies this service that the given environment has made progress on a
   * run.
   *
   * @param environment the name of the environment
   * @param expectMore {@code true} if this service should expect more progress
   *        to be made in this same environment
   */
  public synchronized void recordProgress(String environment,
                                          boolean expectMore) {
    Objects.requireNonNull(environment);

    if (environmentToTask.size() >= config.maxEnvironments
        && !environmentToTask.containsKey(environment)) {
      logger.warn(
          "Ignoring progress from environment {} because there are "
              + "already {} environments running concurrently, "
              + "which is more than expected",
          environment,
          environmentToTask.size());
      return;
    }

    environmentToTask.compute(
        environment,
        (String env, CancellableTask oldTask) -> {

          if (oldTask != null)
            oldTask.cancel();

          if (!expectMore)
            return null;

          return taskScheduler.schedule(
              /* task= */
              () -> {
                synchronized (this) {
                  environmentToTask.remove(environment);
                }
                complain(environment);
              },
              /* delay= */
              Duration.ofSeconds(config.environmentTimeoutSeconds));
        });
  }

  private void complain(String environment) {
    Objects.requireNonNull(environment);

    String subject = environmentCrashedEmailSubject(environment);

    String textContent =
        "tfb-status hasn't received any new data from "
            + "the benchmarking environment \""
            + environment
            + "\" in a while.  Is everything ok?";

    try {
      emailSender.sendEmail(
          /* subject= */ subject,
          /* textContent= */ textContent,
          /* attachments= */ List.of());
    } catch (MessagingException e) {
      logger.warn(
          "Error sending email regarding lack of progress in environment {}",
          environment, e);
    }
  }

  @VisibleForTesting
  static String environmentCrashedEmailSubject(String environment) {
    return "<tfb> <auto> Benchmarking environment \""
        + environment
        + "\" crashed?";
  }
}
