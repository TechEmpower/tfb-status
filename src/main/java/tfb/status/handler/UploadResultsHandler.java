package tfb.status.handler;

import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Methods.POST;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static io.undertow.util.StatusCodes.UNSUPPORTED_MEDIA_TYPE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.WRITE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.MoreFiles;
import com.google.common.net.MediaType;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.glassfish.hk2.api.messaging.Topic;
import org.jvnet.hk2.annotations.ContractsProvided;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.service.Authenticator;
import tfb.status.service.FileStore;
import tfb.status.service.HomeResultsReader;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.util.ZipFiles;
import tfb.status.view.HomePageView.ResultsView;
import tfb.status.view.Results;
import tfb.status.view.UpdatedResultsEvent;

/**
 * Handles requests to upload a file containing results from a TFB run.  The
 * file may either be a results.json file (in which case the {@code
 * Content-Type} of the request must be {@code application/json}) or a zip
 * containing the full output of a run, including logs (in which case the {@code
 * Content-Type} must be {@code application/zip}.
 */
@Singleton
@ContractsProvided(HttpHandler.class)
@ExactPath("/upload")
public final class UploadResultsHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public UploadResultsHandler(FileStore fileStore,
                              Authenticator authenticator,
                              ObjectMapper objectMapper,
                              Topic<UpdatedResultsEvent> updatedResultsTopic,
                              HomeResultsReader homeResultsReader,
                              Clock clock) {

    Objects.requireNonNull(fileStore);
    Objects.requireNonNull(authenticator);
    Objects.requireNonNull(objectMapper);
    Objects.requireNonNull(updatedResultsTopic);
    Objects.requireNonNull(homeResultsReader);
    Objects.requireNonNull(clock);

    Logger logger = LoggerFactory.getLogger(getClass());

    delegate =
        HttpHandlers.chain(
            exchange ->
                internalHandleRequest(
                    exchange,
                    fileStore,
                    updatedResultsTopic,
                    homeResultsReader,
                    objectMapper,
                    clock,
                    logger),
            handler -> new MethodHandler().addMethod(POST, handler),
            handler -> new DisableCacheHandler(handler),
            handler -> authenticator.newRequiredAuthHandler(handler));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }

  private static void internalHandleRequest(HttpServerExchange exchange,
                                            FileStore fileStore,
                                            Topic<UpdatedResultsEvent> updatedResultsTopic,
                                            HomeResultsReader homeResultsReader,
                                            ObjectMapper objectMapper,
                                            Clock clock,
                                            Logger logger)
      throws IOException {

    MediaType contentType = detectMediaType(exchange);
    boolean isJson;
    if (contentType.is(JSON_MEDIA_TYPE))
      isJson = true;
    else if (contentType.is(ZIP_MEDIA_TYPE))
      isJson = false;
    else {
      exchange.setStatusCode(UNSUPPORTED_MEDIA_TYPE);
      return;
    }

    String fileExtension = isJson ? "json" : "zip";

    Path tempFile =
        Files.createTempFile(
            /* prefix= */ "TFB_Status_Upload",
            /* suffix= */ "." + fileExtension);

    try (OutputStream out = Files.newOutputStream(tempFile, WRITE, APPEND)) {
      exchange.getInputStream().transferTo(out);
    }

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

    String uuid = results.uuid;

    class Helper {
      Path newResultsFile() {
        DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern(
                "yyyy-MM-dd-HH-mm-ss-SSS", Locale.ROOT);

        LocalDateTime now = LocalDateTime.now(clock);
        String timestamp = formatter.format(now);

        return fileStore.resultsDirectory().resolve(
            "results." + timestamp + "." + fileExtension);
      }

      Path destinationForIncomingFile() throws IOException {
        if (uuid == null)
          return newResultsFile();

        ResultsView oldResults = homeResultsReader.resultsByUuid(uuid);
        if (oldResults == null)
          return newResultsFile();

        if (oldResults.jsonFileName != null
            && oldResults.jsonFileName.endsWith("." + fileExtension))
          return fileStore.resultsDirectory()
                          .resolve(oldResults.jsonFileName);

        if (oldResults.zipFileName != null
            && oldResults.zipFileName.endsWith("." + fileExtension))
          return fileStore.resultsDirectory()
                          .resolve(oldResults.zipFileName);

        return newResultsFile();
      }
    }

    Path permanentFile = new Helper().destinationForIncomingFile();

    MoreFiles.createParentDirectories(permanentFile);

    Files.move(
        /* source= */ tempFile,
        /* target= */ permanentFile,
        /* options= */ REPLACE_EXISTING);

    if (uuid != null)
      updatedResultsTopic.publish(new UpdatedResultsEvent(uuid));
  }

  private static MediaType detectMediaType(HttpServerExchange exchange) {
    String contentType = exchange.getRequestHeaders().getFirst(CONTENT_TYPE);

    if (contentType == null)
      return MediaType.ANY_TYPE;

    try {
      return MediaType.parse(contentType);
    } catch (IllegalArgumentException ignored) {
      return MediaType.ANY_TYPE;
    }
  }

  private static final MediaType JSON_MEDIA_TYPE =
      MediaType.create("application", "json");

  private static final MediaType ZIP_MEDIA_TYPE =
      MediaType.create("application", "zip");
}
