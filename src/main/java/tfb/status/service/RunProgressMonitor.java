package tfb.status.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.MessagingException;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.messaging.MessageReceiver;
import org.glassfish.hk2.api.messaging.SubscribeTo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.RunProgressMonitorConfig;
import tfb.status.view.HomePageView.ResultsView;
import tfb.status.view.UpdatedResultsEvent;

/**
 * Complains over email when a benchmarking environment has stopped sending
 * updates.
 */
@Singleton
@MessageReceiver
public final class RunProgressMonitor implements PreDestroy {
  private final RunProgressMonitorConfig config;
  private final HomeResultsReader homeResultsReader;
  private final TaskScheduler taskScheduler;
  private final EmailSender emailSender;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @GuardedBy("this")
  private final Map<String, Future<?>> environmentToTask = new HashMap<>();

  @Inject
  public RunProgressMonitor(RunProgressMonitorConfig config,
                            HomeResultsReader homeResultsReader,
                            TaskScheduler taskScheduler,
                            EmailSender emailSender) {

    this.config = Objects.requireNonNull(config);
    this.homeResultsReader = Objects.requireNonNull(homeResultsReader);
    this.taskScheduler = Objects.requireNonNull(taskScheduler);
    this.emailSender = Objects.requireNonNull(emailSender);
  }

  @Override
  public void preDestroy() {
    stop();
  }

  /**
   * Cleans up resources used by this service.
   */
  public synchronized void stop() {
    for (Future<?> task : environmentToTask.values())
      task.cancel(true);

    environmentToTask.clear();
  }

  public void onUpdatedResults(@SubscribeTo UpdatedResultsEvent event)
      throws IOException {

    Objects.requireNonNull(event);

    String uuid = event.uuid;
    ResultsView results = homeResultsReader.resultsByUuid(uuid);
    if (results == null) {
      logger.warn(
          "Result {} not found... what happened?",
          uuid);
      return;
    }

    String environment = results.environmentDescription;
    if (environment == null)
      return;

    // We expect more if (a) a results.json was uploaded but we're still waiting
    // for the results.zip, or (b) if this is a continuous benchmarking
    // environment that starts a new run every time it finishes a run.
    boolean expectMore =
        (results.zipFileName == null) // case (a)
            || environment.equals("Citrine"); // case (b)
    // TODO: It's not great to have "Citrine" hardcoded.  How else can we detect
    //       that this is a continuous benchmarking environment?

    recordProgress(environment, expectMore);
  }

  private synchronized void recordProgress(String environment,
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
        (String env, Future<?> oldTask) -> {

          if (oldTask != null)
            oldTask.cancel(true);

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
          "Error sending email regarding lack of progress in "
              + "environment {}",
          environment,
          e);
    }
  }

  @VisibleForTesting
  static String environmentCrashedEmailSubject(String environment) {
    return "<tfb> <auto> Benchmarking environment \""
        + environment
        + "\" crashed?";
  }
}
