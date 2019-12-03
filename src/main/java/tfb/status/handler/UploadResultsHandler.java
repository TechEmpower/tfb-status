package tfb.status.handler;

import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.Methods.POST;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.stream.Collectors.joining;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharSource;
import com.google.common.io.MoreFiles;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import javax.activation.DataSource;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.MessagingException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.service.Authenticator;
import tfb.status.service.DiffGenerator;
import tfb.status.service.EmailSender;
import tfb.status.service.FileStore;
import tfb.status.service.HomeResultsReader;
import tfb.status.service.RunProgressMonitor;
import tfb.status.undertow.extensions.MediaTypeHandler;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.util.ZipFiles;
import tfb.status.view.HomePageView.ResultsView;
import tfb.status.view.Results;

/**
 * Handles requests to upload a file containing results from a TFB run.  The
 * file may either be a results.json file (in which case the
 * {@code Content-Type} of the request must be {@code application/json}) or a
 * zip containing the full output of a run, including logs (in which case the
 * {@code Content-Type} must be {@code application/zip}.
 */
@Singleton
@ExactPath("/upload")
public final class UploadResultsHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public UploadResultsHandler(FileStore fileStore,
                              Authenticator authenticator,
                              ObjectMapper objectMapper,
                              EmailSender emailSender,
                              HomeUpdatesHandler homeUpdates,
                              HomeResultsReader homeResultsReader,
                              DiffGenerator diffGenerator,
                              Clock clock,
                              RunProgressMonitor runProgressMonitor) {

    var jsonHandler =
        new JsonHandler(
            /* fileStore= */ fileStore,
            /* authenticator= */ authenticator,
            /* homeUpdates=*/ homeUpdates,
            /* homeResultsReader=*/ homeResultsReader,
            /* clock= */ clock,
            /* objectMapper=*/ objectMapper,
            /* runProgressMonitor= */ runProgressMonitor);

    var zipHandler =
        new ZipHandler(
            /* fileStore= */ fileStore,
            /* authenticator= */ authenticator,
            /* homeUpdates=*/ homeUpdates,
            /* homeResultsReader=*/ homeResultsReader,
            /* clock= */ clock,
            /* objectMapper=*/ objectMapper,
            /* emailSender=*/ emailSender,
            /* diffGenerator=*/ diffGenerator,
            /* runProgressMonitor= */ runProgressMonitor);

    HttpHandler handler =
        new MediaTypeHandler().addMediaType("application/json", jsonHandler)
                              .addMediaType("application/zip", zipHandler);

    handler = new MethodHandler().addMethod(POST, handler);
    handler = new DisableCacheHandler(handler);
    handler = authenticator.newRequiredAuthHandler(handler);

    delegate = handler;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }

  /**
   * Shared logic for the .json and .zip handlers.
   */
  private abstract static class BaseHandler implements HttpHandler {
    private final HomeUpdatesHandler homeUpdates;
    private final HomeResultsReader homeResultsReader;
    private final Clock clock;
    private final FileStore fileStore;
    private final String fileExtension;

    BaseHandler(FileStore fileStore,
                Authenticator authenticator,
                HomeUpdatesHandler homeUpdates,
                HomeResultsReader homeResultsReader,
                Clock clock,
                String fileExtension) {

      this.homeUpdates = Objects.requireNonNull(homeUpdates);
      this.homeResultsReader = Objects.requireNonNull(homeResultsReader);
      this.clock = Objects.requireNonNull(clock);
      this.fileStore = Objects.requireNonNull(fileStore);
      this.fileExtension = Objects.requireNonNull(fileExtension);
    }

    /**
     * Saves the request body to a temporary file then validates that file.  If
     * valid, moves the file into the results directory.  Otherwise, deletes the
     * file and responds with {@code 400 Bad Request}.
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      Path tempFile = Files.createTempFile(/* prefix= */ "TFB_Status_Upload",
                                           /* suffix= */ "." + fileExtension);

      try (WritableByteChannel out =
               Files.newByteChannel(tempFile, WRITE, APPEND)) {
        // TODO: Is this safe?
        ReadableByteChannel in = exchange.getRequestChannel();
        ByteStreams.copy(in, out);
      }

      if (!isValidNewFile(tempFile)) {
        Files.delete(tempFile);
        exchange.setStatusCode(BAD_REQUEST);
        return;
      }

      Path permanentFile = destinationForIncomingFile(tempFile);

      MoreFiles.createParentDirectories(permanentFile);

      Files.move(
          /* source= */ tempFile,
          /* target= */ permanentFile,
          /* options= */ REPLACE_EXISTING);

      String uuid = tryReadUuid(permanentFile);
      if (uuid != null)
        homeUpdates.sendUpdate(uuid);

      runPostUploadActions(permanentFile);
    }

    private Path destinationForIncomingFile(Path incomingFile)
        throws IOException {

      String incomingUuid = tryReadUuid(incomingFile);
      if (incomingUuid == null)
        return newResultsFile();

      ResultsView results = homeResultsReader.resultsByUuid(incomingUuid);
      if (results == null)
        return newResultsFile();

      if (results.jsonFileName != null
          && results.jsonFileName.endsWith("." + fileExtension))
        return fileStore.resultsDirectory().resolve(results.jsonFileName);

      if (results.zipFileName != null
          && results.zipFileName.endsWith("." + fileExtension))
        return fileStore.resultsDirectory().resolve(results.zipFileName);

      return newResultsFile();
    }

    private Path newResultsFile() {
      DateTimeFormatter formatter =
          DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS", Locale.ROOT);

      LocalDateTime now = LocalDateTime.now(clock);
      String timestamp = formatter.format(now);

      return fileStore.resultsDirectory().resolve(
          "results." + timestamp + "." + fileExtension);
    }

    /**
     * Returns the {@linkplain Results#uuid uuid} of the given file or {@code
     * null} if the uuid cannot be determined.
     */
    abstract @Nullable String tryReadUuid(Path file);

    /**
     * Returns the {@linkplain Results#environmentDescription environment} of
     * the given file or {@code null} if the environment cannot be determined.
     */
    abstract @Nullable String tryReadEnvironment(Path file);

    /**
     * Returns {@code true} if the given newly-uploaded file is in the correct
     * format as required by this handler.
     */
    abstract boolean isValidNewFile(Path newFile);

    /**
     * Executes any post-upload actions for the given newly-uploaded,
     * already-validated file.
     */
    abstract void runPostUploadActions(Path newFile);
  }

  private static final class JsonHandler extends BaseHandler {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ObjectMapper objectMapper;
    private final RunProgressMonitor runProgressMonitor;

    JsonHandler(FileStore fileStore,
                Authenticator authenticator,
                HomeUpdatesHandler homeUpdates,
                HomeResultsReader homeResultsReader,
                Clock clock,
                ObjectMapper objectMapper,
                RunProgressMonitor runProgressMonitor) {

      super(
          /* fileStore= */ fileStore,
          /* authenticator= */ authenticator,
          /* homeUpdates= */ homeUpdates,
          /* homeResultsReader= */ homeResultsReader,
          /* clock= */ clock,
          /* fileExtension= */ "json");

      this.objectMapper = Objects.requireNonNull(objectMapper);
      this.runProgressMonitor = Objects.requireNonNull(runProgressMonitor);
    }

    @Override
    @Nullable String tryReadUuid(Path jsonFile) {
      Objects.requireNonNull(jsonFile);
      Results parsed;
      try (InputStream inputStream = Files.newInputStream(jsonFile)) {
        parsed = objectMapper.readValue(inputStream, Results.class);
      } catch (IOException ignored) {
        return null;
      }
      return parsed.uuid;
    }

    @Override
    @Nullable String tryReadEnvironment(Path jsonFile) {
      Objects.requireNonNull(jsonFile);
      Results results;
      try (InputStream inputStream = Files.newInputStream(jsonFile)) {
        results = objectMapper.readValue(inputStream, Results.class);
      } catch (IOException ignored) {
        return null;
      }
      return results.environmentDescription;
    }

    @Override
    boolean isValidNewFile(Path newJsonFile) {
      Objects.requireNonNull(newJsonFile);
      try (InputStream inputStream = Files.newInputStream(newJsonFile)) {
        objectMapper.readValue(inputStream, Results.class);
        return true;
      } catch (IOException e) {
        logger.warn("Exception validating json file {}", newJsonFile, e);
        return false;
      }
    }

    @Override
    void runPostUploadActions(Path newJsonFile) {
      String environment = tryReadEnvironment(newJsonFile);
      if (environment != null) {
        runProgressMonitor.recordProgress(
            /* environment= */ environment,
            /* expectMore= */ true);
      }
    }
  }

  private static final class ZipHandler extends BaseHandler {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final FileStore fileStore;
    private final ObjectMapper objectMapper;
    private final EmailSender emailSender;
    private final DiffGenerator diffGenerator;
    private final Clock clock;
    private final RunProgressMonitor runProgressMonitor;

    ZipHandler(FileStore fileStore,
               Authenticator authenticator,
               HomeUpdatesHandler homeUpdates,
               HomeResultsReader homeResultsReader,
               Clock clock,
               ObjectMapper objectMapper,
               EmailSender emailSender,
               DiffGenerator diffGenerator,
               RunProgressMonitor runProgressMonitor) {

      super(
          /* fileStore= */ fileStore,
          /* authenticator= */ authenticator,
          /* homeUpdates= */ homeUpdates,
          /* homeResultsReader= */ homeResultsReader,
          /* clock= */ clock,
          /* fileExtension= */ "zip");

      this.fileStore = Objects.requireNonNull(fileStore);
      this.objectMapper = Objects.requireNonNull(objectMapper);
      this.clock = Objects.requireNonNull(clock);
      this.emailSender = Objects.requireNonNull(emailSender);
      this.diffGenerator = Objects.requireNonNull(diffGenerator);
      this.runProgressMonitor = Objects.requireNonNull(runProgressMonitor);
    }

    @Override
    @Nullable String tryReadUuid(Path zipFile) {
      Objects.requireNonNull(zipFile);
      Results parsed;
      try {
        parsed =
            ZipFiles.readZipEntry(
                /* zipFile= */ zipFile,
                /* entryPath= */ "results.json",
                /* entryReader= */ inputStream ->
                                       objectMapper.readValue(inputStream,
                                                              Results.class));
      } catch (IOException ignored) {
        return null;
      }
      return (parsed == null) ? null : parsed.uuid;
    }

    @Override
    @Nullable String tryReadEnvironment(Path zipFile) {
      Objects.requireNonNull(zipFile);
      Results results;
      try {
        results =
            ZipFiles.readZipEntry(
                /* zipFile= */ zipFile,
                /* entryPath= */ "results.json",
                /* entryReader= */ inputStream ->
                                       objectMapper.readValue(inputStream,
                                                              Results.class));
      } catch (IOException ignored) {
        return null;
      }
      return (results == null) ? null : results.environmentDescription;
    }

    @Override
    boolean isValidNewFile(Path newZipFile) {
      Objects.requireNonNull(newZipFile);
      Results results;
      try {
        results =
            ZipFiles.readZipEntry(
                /* zipFile= */ newZipFile,
                /* entryPath= */ "results.json",
                /* entryReader= */ inputStream ->
                                       objectMapper.readValue(inputStream,
                                                              Results.class));
      } catch (IOException e) {
        logger.warn("Exception validating zip file {}", newZipFile, e);
        return false;
      }
      return results != null;
    }

    @Override
    void runPostUploadActions(Path newZipFile) {
      Objects.requireNonNull(newZipFile);

      byte[] rawResultsBytes = findResultsBytes(newZipFile);
      if (rawResultsBytes == null) {
        logger.warn(
            "Ignoring new zip file {} because no results.json was found inside",
            newZipFile);
        return;
      }

      maybeSendEmail(
          /* newZipFile= */ newZipFile,
          /* rawResultsBytes= */ rawResultsBytes);

      String environment = tryReadEnvironment(newZipFile);
      if (environment != null) {
        // TODO: It's not great to have "Citrine" hardcoded.  How else can we
        //       detect that this is a continuous benchmarking environment?
        runProgressMonitor.recordProgress(
            /* environment= */ environment,
            /* expectMore= */ "Citrine".equals(environment));
      }
    }

    //
    // Avoid sending an email if it's too soon since the last email.  Once, we
    // had an issue where the TFB server completed 200+ runs over a single
    // weekend and sent an email at the end of each run.  Obviously something
    // was wrong with the TFB server for it to complete so many runs so quickly,
    // but this application is responsible for deciding whether to send emails.
    //

    @GuardedBy("emailTimeLock")
    private volatile @Nullable Instant previousEmailTime;
    private final Object emailTimeLock = new Object();
    private static final Duration MIN_TIME_BETWEEN_EMAILS = Duration.ofHours(24);

    private void maybeSendEmail(Path newZipFile, byte[] rawResultsBytes) {

      synchronized (emailTimeLock) {
        Instant now = clock.instant();
        Instant previous = this.previousEmailTime;
        if (previous != null) {
          Instant nextEmailTime = previous.plus(MIN_TIME_BETWEEN_EMAILS);
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

      var minifiedResults =
          new Results.TfbWebsiteView(
              /* name= */ results.name,
              /* completionTime= */ results.completionTime,
              /* duration= */ results.duration,
              /* queryIntervals= */ results.queryIntervals,
              /* concurrencyLevels= */ results.concurrencyLevels,
              /* rawData= */ results.rawData,
              /* failed= */ results.failed);

      byte[] minifiedResultsBytes;
      try {
        minifiedResultsBytes = objectMapper.writeValueAsBytes(minifiedResults);
      } catch (IOException impossible) {
        throw new AssertionError(
            "The TFB website view of results is always JSON-serializable",
            impossible);
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
              /* minifiedResultsBytes= */ minifiedResultsBytes,
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
               Files.newDirectoryStream(fileStore.resultsDirectory(),
                                        "*.zip")) {

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

    private <T> @Nullable T tryReadZipEntry(Path zipFile,
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
        byte[] minifiedResultsBytes,
        byte@Nullable[] testMetadataBytes,
        @Nullable String diff) {

      var attachments = new ImmutableList.Builder<DataSource>();

      attachments.add(
          emailSender.createAttachment(
              /* fileName= */ "results.json",
              /* mediaType= */ JSON_UTF_8,
              /* fileBytes= */ ByteSource.wrap(rawResultsBytes)));

      attachments.add(
          emailSender.createAttachment(
              /* fileName= */ "results.min.json",
              /* mediaType= */ JSON_UTF_8,
              /* fileBytes= */ ByteSource.wrap(minifiedResultsBytes)));

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
  }

  @VisibleForTesting
  static String runCompleteEmailSubject(Results results) {
    return "<tfb> <auto> Run complete: " + results.name;
  }
}
