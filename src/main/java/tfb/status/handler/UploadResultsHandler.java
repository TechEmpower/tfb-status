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
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharSource;
import com.google.common.io.MoreFiles;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import javax.activation.DataSource;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.FileStoreConfig;
import tfb.status.service.Authenticator;
import tfb.status.service.DiffGenerator;
import tfb.status.service.EmailSender;
import tfb.status.undertow.extensions.MediaTypeHandler;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.util.ZipFiles;
import tfb.status.view.ParsedResults;
import tfb.status.view.ParsedResults.UuidOnly;

/**
 * Handles requests to upload a file containing results from a TFB run.  The
 * file may either be a results.json file (in which case the
 * {@code Content-Type} of the request must be {@code application/json}) or a
 * zip containing the full output of a run, including logs (in which case the
 * {@code Content-Type} must be {@code application/zip}.
 */
@Singleton
public final class UploadResultsHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public UploadResultsHandler(FileStoreConfig fileStoreConfig,
                              Authenticator authenticator,
                              ObjectMapper objectMapper,
                              EmailSender emailSender,
                              HomeUpdatesHandler homeUpdates,
                              DiffGenerator diffGenerator,
                              Clock clock) {

    HttpHandler jsonFileHandler =
        new NewJsonFileHandler(
            /* fileStoreConfig= */ fileStoreConfig,
            /* authenticator= */ authenticator,
            /* homeUpdates=*/ homeUpdates,
            /* clock= */ clock,
            /* objectMapper=*/ objectMapper);

    HttpHandler zipFileHandler =
        new NewZipFileHandler(
            /* fileStoreConfig= */ fileStoreConfig,
            /* authenticator= */ authenticator,
            /* homeUpdates=*/ homeUpdates,
            /* clock= */ clock,
            /* objectMapper=*/ objectMapper,
            /* emailSender=*/ emailSender,
            /* diffGenerator=*/ diffGenerator);

    HttpHandler handler =
        new MediaTypeHandler().addMediaType("application/json", jsonFileHandler)
                              .addMediaType("application/zip", zipFileHandler);

    handler = new MethodHandler().addMethod(POST, handler);
    handler = new DisableCacheHandler(handler);
    handler = authenticator.newRequiredAuthHandler(handler);

    delegate = handler;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }

  private abstract static class NewFileHandler implements HttpHandler {
    private final HomeUpdatesHandler homeUpdates;
    private final Clock clock;
    private final Path resultsDirectory;
    private final String fileExtension;

    NewFileHandler(FileStoreConfig fileStoreConfig,
                   Authenticator authenticator,
                   HomeUpdatesHandler homeUpdates,
                   Clock clock,
                   String fileExtension) {
      this.homeUpdates = Objects.requireNonNull(homeUpdates);
      this.clock = Objects.requireNonNull(clock);
      this.resultsDirectory = Paths.get(fileStoreConfig.resultsDirectory);
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

    private Path destinationForIncomingFile(Path incomingFile) {
      if (!Files.isDirectory(resultsDirectory))
        return newResultsFile();

      String incomingUuid = tryReadUuid(incomingFile);
      if (incomingUuid == null)
        return newResultsFile();

      try (DirectoryStream<Path> candidateFiles =
               Files.newDirectoryStream(resultsDirectory, "*." + fileExtension)) {

        for (Path existingFile : candidateFiles) {
          String existingUuid = tryReadUuid(existingFile);
          if (incomingUuid.equals(existingUuid))
            // TODO: Also check if the file was updated more recently than ours?
            return existingFile;
        }

      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }

      return newResultsFile();
    }

    private Path newResultsFile() {
      DateTimeFormatter formatter =
          DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS", Locale.ROOT);

      LocalDateTime now = LocalDateTime.now(clock);
      String timestamp = formatter.format(now);

      return resultsDirectory.resolve(
          "results." + timestamp + "." + fileExtension);
    }

    /**
     * Returns the {@linkplain ParsedResults#uuid uuid} of the given file or
     * {@code null} if the uuid cannot be determined.
     */
    @Nullable
    abstract String tryReadUuid(Path file);

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

  private static final class NewJsonFileHandler extends NewFileHandler {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ObjectMapper objectMapper;

    NewJsonFileHandler(FileStoreConfig fileStoreConfig,
                       Authenticator authenticator,
                       HomeUpdatesHandler homeUpdates,
                       Clock clock,
                       ObjectMapper objectMapper) {

      super(
          /* fileStoreConfig= */ fileStoreConfig,
          /* authenticator= */ authenticator,
          /* homeUpdates= */ homeUpdates,
          /* clock= */ clock,
          /* fileExtension= */ "json");

      this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Nullable
    @Override
    String tryReadUuid(Path jsonFile) {
      Objects.requireNonNull(jsonFile);
      UuidOnly parsed;
      try {
        parsed = objectMapper.readValue(jsonFile.toFile(), UuidOnly.class);
      } catch (IOException ignored) {
        return null;
      }
      return parsed.uuid;
    }

    @Override
    boolean isValidNewFile(Path newJsonFile) {
      Objects.requireNonNull(newJsonFile);
      try {
        objectMapper.readValue(newJsonFile.toFile(), ParsedResults.class);
        return true;
      } catch (IOException e) {
        logger.warn("Exception validating json file {}", newJsonFile, e);
        return false;
      }
    }

    @Override
    void runPostUploadActions(Path newJsonFile) {
      // Do nothing.
    }
  }

  private static final class NewZipFileHandler extends NewFileHandler {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ObjectMapper objectMapper;
    private final EmailSender emailSender;
    private final DiffGenerator diffGenerator;
    private final Clock clock;

    NewZipFileHandler(FileStoreConfig fileStoreConfig,
                      Authenticator authenticator,
                      HomeUpdatesHandler homeUpdates,
                      Clock clock,
                      ObjectMapper objectMapper,
                      EmailSender emailSender,
                      DiffGenerator diffGenerator) {

      super(
          /* fileStoreConfig= */ fileStoreConfig,
          /* authenticator= */ authenticator,
          /* homeUpdates= */ homeUpdates,
          /* clock= */ clock,
          /* fileExtension= */ "zip");

      this.objectMapper = Objects.requireNonNull(objectMapper);
      this.clock = Objects.requireNonNull(clock);
      this.emailSender = Objects.requireNonNull(emailSender);
      this.diffGenerator = Objects.requireNonNull(diffGenerator);
    }

    @Nullable
    @Override
    String tryReadUuid(Path zipFile) {
      Objects.requireNonNull(zipFile);
      UuidOnly parsed;
      try {
        parsed =
            ZipFiles.readZipEntry(
                /* zipFile= */ zipFile,
                /* entryPath= */ "results.json",
                /* entryReader= */ in -> objectMapper.readValue(in, UuidOnly.class));
      } catch (IOException ignored) {
        return null;
      }
      return (parsed == null) ? null : parsed.uuid;
    }

    @Override
    boolean isValidNewFile(Path newZipFile) {
      Objects.requireNonNull(newZipFile);
      ParsedResults results;
      try {
        results =
            ZipFiles.readZipEntry(
                /* zipFile= */ newZipFile,
                /* entryPath= */ "results.json",
                /* entryReader= */ in -> objectMapper.readValue(in, ParsedResults.class));
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
    }

    //
    // Avoid sending an email if it's too soon since the last email.  Once, we
    // had an issue where the TFB server completed 200+ runs over a single
    // weekend and sent an email at the end of each run.  Obviously something
    // was wrong with the TFB server for it to complete so many runs so quickly,
    // but this application is responsible for deciding whether to send emails.
    //

    @GuardedBy("emailTimeLock")
    @Nullable private volatile Instant previousEmailTime;
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

    private void definitelySendEmail(Path newZipFile,
                                     byte[] rawResultsBytes) {

      ParsedResults results;
      try {
        results = objectMapper.readValue(rawResultsBytes, ParsedResults.class);
      } catch (IOException e) {
        logger.warn(
            "Ignoring new zip file {} because of a JSON parse error",
            newZipFile, e);
        return;
      }

      byte[] minifiedResultsBytes;
      try {
        minifiedResultsBytes =
            objectMapper.writeValueAsBytes(
                new ParsedResults.TfbWebsiteView(
                    /* name= */ results.name,
                    /* completionTime= */ results.completionTime,
                    /* duration= */ results.duration,
                    /* queryIntervals= */ results.queryIntervals,
                    /* concurrencyLevels= */ results.concurrencyLevels,
                    /* rawData= */ results.rawData,
                    /* failed= */ results.failed));
      } catch (IOException impossible) {
        throw new AssertionError(
            "The TFB website view of results is always JSON-serializable",
            impossible);
      }

      byte[] testMetadataBytes = findTestMetadataBytes(newZipFile);
      boolean isTestMetadataPresent = testMetadataBytes != null;
      String newCommitId = findCommitId(newZipFile);
      Path previousZipFile = findPreviousZipFile(newZipFile);

      ParsedResults previousResults =
          (previousZipFile == null)
              ? null
              : findResults(previousZipFile);

      String previousCommitId =
          (previousZipFile == null)
              ? null
              : findCommitId(previousZipFile);

      String diff =
          (previousResults == null)
              ? null
              : diffGenerator.diff(previousResults, results);

      String subject = "Run complete: " + results.name;

      String textContent =
          prepareEmailBody(
              /* results= */ results,
              /* isTestMetadataPresent= */ isTestMetadataPresent,
              /* previousCommitId= */ previousCommitId,
              /* newCommitId= */ newCommitId);

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
                     .map(DataSource::getName)
                     .collect(joining(", ")));

      try {
        emailSender.sendEmail(subject, textContent, attachments);
      } catch (MessagingException e) {
        logger.warn(
            "Error sending email for new zip file {}",
             newZipFile, e);
      }
    }

    @Nullable
    private Path findPreviousZipFile(Path newZipFile) {
      // TODO: Is there a better way to find the previous zip file?
      Path previousZipFile = null;
      FileTime previousTime = null;
      Path directory = newZipFile.getParent();
      if (directory == null)
        return null;

      try (DirectoryStream<Path> allZipFiles =
               Files.newDirectoryStream(directory, "*.zip")) {

        for (Path file : allZipFiles) {
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

    @Nullable
    private String findCommitId(Path zipFile) {
      return tryReadZipEntry(
          /* zipFile= */ zipFile,
          /* entryPath= */ "commit_id.txt",
          /* entryReader= */
          in -> {
            String firstLine;
            try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(in, UTF_8))) {
              firstLine = reader.readLine();
            }
            return (firstLine == null) ? "" : firstLine;
          });
    }

    @Nullable
    private byte[] findResultsBytes(Path zipFile) {
      return tryReadZipEntry(
          /* zipFile= */ zipFile,
          /* entryPath= */ "results.json",
          /* entryReader= */ InputStream::readAllBytes);
    }

    @Nullable
    private byte[] findTestMetadataBytes(Path zipFile) {
      return tryReadZipEntry(
          /* zipFile= */ zipFile,
          /* entryPath= */ "test_metadata.json",
          /* entryReader= */ InputStream::readAllBytes);
    }

    @Nullable
    private ParsedResults findResults(Path zipFile) {
      return tryReadZipEntry(
          /* zipFile= */ zipFile,
          /* entryPath= */ "results.json",
          /* entryReader= */ in -> objectMapper.readValue(in, ParsedResults.class));
    }

    @Nullable
    private <T> T tryReadZipEntry(Path zipFile,
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

    private String prepareEmailBody(ParsedResults results,
                                    boolean isTestMetadataPresent,
                                    @Nullable String previousCommitId,
                                    @Nullable String newCommitId) {

      // TODO: Consider using a template engine.
      StringBuilder sb = new StringBuilder();
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

      if (isTestMetadataPresent)
        sb.append("The test_metadata.json file is also attached.\n");
      else
        sb.append("There was no test_metadata.json file included in the results.\n");

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
        @Nullable byte[] testMetadataBytes,
        @Nullable String diff) {

      ImmutableList.Builder<DataSource> attachments = ImmutableList.builder();

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
}
