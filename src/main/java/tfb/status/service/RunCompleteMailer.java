package tfb.status.service;

import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import javax.activation.DataSource;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.MessagingException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.messaging.MessageReceiver;
import org.glassfish.hk2.api.messaging.SubscribeTo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.RunCompleteMailerConfig;
import tfb.status.util.ZipFiles;
import tfb.status.view.HomePageView.ResultsView;
import tfb.status.view.Results;
import tfb.status.view.UpdatedResultsEvent;

/**
 * Sends an email when a run completes.
 */
@Singleton
@MessageReceiver
public final class RunCompleteMailer {
  private final RunCompleteMailerConfig config;
  private final Clock clock;
  private final EmailSender emailSender;
  private final ObjectMapper objectMapper;
  private final DiffGenerator diffGenerator;
  private final FileStore fileStore;
  private final HomeResultsReader homeResultsReader;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Inject
  public RunCompleteMailer(RunCompleteMailerConfig config,
                           Clock clock,
                           EmailSender emailSender,
                           ObjectMapper objectMapper,
                           DiffGenerator diffGenerator,
                           FileStore fileStore,
                           HomeResultsReader homeResultsReader) {

    this.config = Objects.requireNonNull(config);
    this.clock = Objects.requireNonNull(clock);
    this.emailSender = Objects.requireNonNull(emailSender);
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.diffGenerator = Objects.requireNonNull(diffGenerator);
    this.fileStore = Objects.requireNonNull(fileStore);
    this.homeResultsReader = Objects.requireNonNull(homeResultsReader);
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

    // A run is complete once it has results.zip file.
    if (results.zipFileName == null)
      return;

    Path newZipFile =
        fileStore.resultsDirectory().resolve(results.zipFileName);

    byte[] rawResultsBytes = findResultsBytes(newZipFile);
    if (rawResultsBytes == null) {
      logger.warn(
          "Ignoring new zip file {} because no results.json was found inside",
          newZipFile);
      return;
    }

    maybeSendEmail(newZipFile, rawResultsBytes);
  }

  //
  // Avoid sending an email if it's too soon since the last email.  Once, we had
  // an issue where the TFB server completed 200+ runs over a single weekend and
  // sent an email at the end of each run.  Obviously something was wrong with
  // the TFB server for it to complete so many runs so quickly, but this
  // application is responsible for deciding whether to send emails.
  //

  @GuardedBy("emailTimeLock")
  private volatile @Nullable Instant previousEmailTime;
  private final Object emailTimeLock = new Object();

  private void maybeSendEmail(Path newZipFile, byte[] rawResultsBytes) {
    synchronized (emailTimeLock) {
      Instant now = clock.instant();
      Instant previous = this.previousEmailTime;
      if (previous != null) {
        Instant nextEmailTime = previous.plusSeconds(config.minSecondsBetweenEmails);
        if (now.isBefore(nextEmailTime)) {
          logger.warn(
              "Suppressing email for new zip file {} because "
                  + "another email was sent for that account too recently, "
                  + "previous email time = {}, next possible email time = {}",
              newZipFile,
              previous,
              nextEmailTime);
          return;
        }
      }
      this.previousEmailTime = now;
    }

    definitelySendEmail(
        /* newZipFile= */ newZipFile,
        /* rawResultsBytes= */ rawResultsBytes);
  }

  private void definitelySendEmail(Path newZipFile, byte[] rawResultsBytes) {
    Results results;
    try {
      results = objectMapper.readValue(rawResultsBytes, Results.class);
    } catch (IOException e) {
      logger.warn(
          "Ignoring new zip file {} because of a JSON parse error",
          newZipFile, e);
      return;
    }

    byte[] testMetadataBytes = findTestMetadataBytes(newZipFile);
    Path previousZipFile = findPreviousZipFile(newZipFile);

    Results previousResults =
        (previousZipFile == null)
            ? null
            : findResults(previousZipFile);

    if (previousResults != null
        && !areResultsComparable(results, previousResults)) {
      previousResults = null;
    }

    String diff =
        (previousResults == null)
            ? null
            : diffGenerator.diff(previousResults, results);

    String subject = runCompleteEmailSubject(results);

    String textContent =
        prepareEmailBody(
            /* results= */ results,
            /* previousResults= */ previousResults,
            /* isTestMetadataPresent= */ testMetadataBytes != null);

    ImmutableList<DataSource> attachments =
        prepareEmailAttachments(
            /* rawResultsBytes= */ rawResultsBytes,
            /* testMetadataBytes= */ testMetadataBytes,
            /* diff= */ diff);

    logger.info(
        "Sending email for new zip file {} with {} attachments {}",
        newZipFile,
        attachments.size(),
        attachments.stream()
                   .map(attachment -> attachment.getName())
                   .collect(joining(", ")));

    try {
      emailSender.sendEmail(subject, textContent, attachments);
    } catch (MessagingException e) {
      logger.warn(
          "Error sending email for new zip file {}",
          newZipFile, e);
    }
  }

  private @Nullable Path findPreviousZipFile(Path newZipFile) {
    Path previousZipFile = null;
    FileTime previousTime = null;

    try (DirectoryStream<Path> zipFiles =
             Files.newDirectoryStream(fileStore.resultsDirectory(), "*.zip")) {

      for (Path file : zipFiles) {
        if (file.equals(newZipFile))
          continue;

        FileTime time = Files.getLastModifiedTime(file);

        if (previousZipFile == null || time.compareTo(previousTime) > 0) {
          previousZipFile = file;
          previousTime = time;
        }
      }

    } catch (IOException e) {
      logger.warn(
          "Error finding predecessor of new zip file {}",
          newZipFile, e);
      return null;
    }

    return previousZipFile;
  }

  private static boolean areResultsComparable(Results a, Results b) {
    Objects.requireNonNull(a);
    Objects.requireNonNull(b);
    return a.environmentDescription != null
        && b.environmentDescription != null
        && a.environmentDescription.equals(b.environmentDescription)
        && a.git != null
        && b.git != null
        && a.git.branchName != null
        && b.git.branchName != null
        && a.git.branchName.equals(b.git.branchName)
        && a.git.repositoryUrl.equals(b.git.repositoryUrl);
  }

  private byte@Nullable[] findResultsBytes(Path zipFile) {
    return tryReadZipEntry(
        /* zipFile= */ zipFile,
        /* entryPath= */ "results.json",
        /* entryReader= */ entry -> entry.readAllBytes());
  }

  private byte@Nullable[] findTestMetadataBytes(Path zipFile) {
    return tryReadZipEntry(
        /* zipFile= */ zipFile,
        /* entryPath= */ "test_metadata.json",
        /* entryReader= */ entry -> entry.readAllBytes());
  }

  private @Nullable Results findResults(Path zipFile) {
    return tryReadZipEntry(
        /* zipFile= */ zipFile,
        /* entryPath= */ "results.json",
        /* entryReader= */ inputStream ->
                               objectMapper.readValue(inputStream,
                                                      Results.class));
  }

  private <T> @Nullable T tryReadZipEntry(
      Path zipFile,
      String entryPath,
      ZipFiles.ZipEntryReader<T> entryReader) {

    T value;
    try {
      value = ZipFiles.readZipEntry(zipFile, entryPath, entryReader);
    } catch (IOException e) {
      logger.warn(
          "Error reading {} from zip file {}",
          entryPath, zipFile, e);
      return null;
    }

    if (value == null)
      logger.warn(
          "No {} found in zip file {}",
          entryPath, zipFile);

    return value;
  }

  private String prepareEmailBody(Results results,
                                  @Nullable Results previousResults,
                                  boolean isTestMetadataPresent) {

    Objects.requireNonNull(results);

    String newCommitId =
        (results.git == null)
            ? null
            : results.git.commitId;

    String previousCommitId =
        (previousResults == null || previousResults.git == null)
            ? null
            : previousResults.git.commitId;

    // TODO: Consider using a template engine.
    var sb = new StringBuilder();
    sb.append("Hello,\n");
    sb.append("\n");
    sb.append("A TFB run has completed in this environment:\n");
    sb.append("\n");
    sb.append("  ");
    sb.append(results.environmentDescription);
    sb.append("\n");
    sb.append("\n");
    sb.append("The results.json file is attached.\n");
    sb.append("\n");

    if (results.uuid != null) {
      sb.append("Details: ");
      sb.append("https://tfb-status.techempower.com/results/");
      sb.append(results.uuid);
      sb.append("\n");
      sb.append("\n");
    }

    if (isTestMetadataPresent)
      sb.append("The test_metadata.json file is also attached.\n");
    else
      sb.append("No test_metadata.json file was included with the results.\n");

    sb.append("\n");
    sb.append("Commit id from previous run: ");

    if (previousCommitId == null)
      sb.append("(unknown)");
    else
      sb.append(previousCommitId);

    sb.append("\n");
    sb.append("Commit id from this run:     ");

    if (newCommitId == null)
      sb.append("(unknown)");
    else
      sb.append(newCommitId);

    sb.append("\n");
    sb.append("\n");

    if (previousCommitId != null
        && newCommitId != null
        && !previousCommitId.equals(newCommitId)) {

      // TODO: Read repository URLs from the files as well?
      sb.append("Source code diff:\n");
      sb.append("https://github.com/TechEmpower/FrameworkBenchmarks/compare/");
      sb.append(previousCommitId);
      sb.append("...");
      sb.append(newCommitId);
      sb.append("\n");
      sb.append("\n");
    }

    sb.append("-a robot");
    return sb.toString();
  }

  private ImmutableList<DataSource> prepareEmailAttachments(
      byte[] rawResultsBytes,
      byte@Nullable[] testMetadataBytes,
      @Nullable String diff) {

    var attachments = new ImmutableList.Builder<DataSource>();

    attachments.add(
        emailSender.createAttachment(
            /* fileName= */ "results.json",
            /* mediaType= */ JSON_UTF_8,
            /* fileBytes= */ ByteSource.wrap(rawResultsBytes)));

    if (testMetadataBytes != null)
      attachments.add(
          emailSender.createAttachment(
              /* fileName= */ "test_metadata.json",
              /* mediaType= */ JSON_UTF_8,
              /* fileBytes= */ ByteSource.wrap(testMetadataBytes)));

    if (diff != null)
      attachments.add(
          emailSender.createAttachment(
              /* fileName= */ "diff.html",
              /* mediaType= */ HTML_UTF_8,
              /* fileBytes= */ CharSource.wrap(diff).asByteSource(UTF_8)));

    return attachments.build();
  }

  @VisibleForTesting
  public static String runCompleteEmailSubject(Results results) {
    return "<tfb> <auto> Run complete: " + results.name;
  }
}
