package tfb.status.handler;

import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.MoreFiles;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.messaging.Topic;
import org.glassfish.hk2.extras.provides.Provides;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.handler.routing.DisableCache;
import tfb.status.handler.routing.Route;
import tfb.status.service.Authenticator;
import tfb.status.service.FileStore;
import tfb.status.service.HomeResultsReader;
import tfb.status.util.ZipFiles;
import tfb.status.view.HomePageView.ResultsView;
import tfb.status.view.Results;
import tfb.status.view.UpdatedResultsEvent;

/**
 * Handles requests to upload a file containing results from a TFB run.  The
 * file may either be a results.json file (in which case the {@code
 * Content-Type} of the request must be {@code application/json}) or a zip
 * containing the full output of a run, including logs (in which case the {@code
 * Content-Type} must be {@code application/zip}).
 */
@Singleton
public final class UploadResultsHandler implements HttpHandler {
  private final FileStore fileStore;
  private final ObjectMapper objectMapper;
  private final Topic<UpdatedResultsEvent> updatedResultsTopic;
  private final HomeResultsReader homeResultsReader;
  private final Clock clock;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Inject
  public UploadResultsHandler(FileStore fileStore,
                              ObjectMapper objectMapper,
                              Topic<UpdatedResultsEvent> updatedResultsTopic,
                              HomeResultsReader homeResultsReader,
                              Clock clock) {

    this.fileStore = Objects.requireNonNull(fileStore);
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.updatedResultsTopic = Objects.requireNonNull(updatedResultsTopic);
    this.homeResultsReader = Objects.requireNonNull(homeResultsReader);
    this.clock = Objects.requireNonNull(clock);
  }

  @Provides
  @Singleton
  @Route(method = "POST", path = "/upload", consumes = "application/json")
  @Route(method = "POST", path = "/upload", consumes = "application/zip")
  @DisableCache
  public HttpHandler uploadResultsHandler(Authenticator authenticator) {
    Objects.requireNonNull(authenticator);
    return authenticator.newRequiredAuthHandler(this);
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws IOException {
    boolean isJson =
        exchange.getAttachment(Route.MATCHED_ROUTE)
                .consumes()
                .equals("application/json");

    String fileExtension = isJson ? "json" : "zip";

    Path tempFile =
        Files.createTempFile(
            /* prefix= */ "TFB_Status_Upload",
            /* suffix= */ "." + fileExtension);

    Files.copy(exchange.getInputStream(), tempFile, REPLACE_EXISTING);

    Results results;
    if (isJson) {
      try (InputStream inputStream = Files.newInputStream(tempFile)) {
        results = objectMapper.readValue(inputStream, Results.class);
      } catch (IOException e) {
        logger.warn("Error validating json file {}", tempFile, e);
        results = null;
      }
    } else {
      try {
        results =
            ZipFiles.readZipEntry(
                tempFile,
                "results.json",
                inputStream ->
                    objectMapper.readValue(inputStream, Results.class));
      } catch (IOException e) {
        logger.warn("Error validating zip file {}", tempFile, e);
        results = null;
      }
    }

    if (results == null) {
      Files.delete(tempFile);
      exchange.setStatusCode(BAD_REQUEST);
      return;
    }

    String uuid = results.uuid();

    Path permanentFile = destinationForIncomingFile(uuid, fileExtension);

    MoreFiles.createParentDirectories(permanentFile);

    Files.move(
        /* source= */ tempFile,
        /* target= */ permanentFile,
        /* options...= */ REPLACE_EXISTING);

    if (uuid != null)
      updatedResultsTopic.publish(new UpdatedResultsEvent(uuid));
  }

  private Path newResultsFile(String fileExtension) {
    Objects.requireNonNull(fileExtension);

    DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern(
            "yyyy-MM-dd-HH-mm-ss-SSS", Locale.ROOT);

    LocalDateTime now = LocalDateTime.now(clock);
    String timestamp = formatter.format(now);

    return fileStore.resultsDirectory().resolve(
        "results." + timestamp + "." + fileExtension);
  }

  private Path destinationForIncomingFile(@Nullable String uuid,
                                          String fileExtension)
      throws IOException {

    Objects.requireNonNull(fileExtension);

    if (uuid == null)
      return newResultsFile(fileExtension);

    ResultsView oldResults = homeResultsReader.resultsByUuid(uuid);
    if (oldResults == null)
      return newResultsFile(fileExtension);

    if (oldResults.jsonFileName() != null
        && oldResults.jsonFileName().endsWith("." + fileExtension))
      return fileStore.resultsDirectory()
                      .resolve(oldResults.jsonFileName());

    if (oldResults.zipFileName() != null
        && oldResults.zipFileName().endsWith("." + fileExtension))
      return fileStore.resultsDirectory()
                      .resolve(oldResults.zipFileName());

    return newResultsFile(fileExtension);
  }
}
