package tfb.status.handler;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static io.undertow.util.StatusCodes.NOT_FOUND;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.MoreFiles;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.service.FileStore;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.util.ZipFiles;
import tfb.status.view.Results;

/**
 * Handles requests to export results.json files in the format used by the TFB
 * website.
 */
@Singleton
public final class ExportResultsHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public ExportResultsHandler(FileStore fileStore,
                              ObjectMapper objectMapper) {

    HttpHandler handler = new CoreHandler(fileStore, objectMapper);

    handler = new MethodHandler().addMethod(GET, handler);
    handler = new DisableCacheHandler(handler);

    delegate = handler;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }

  private static final class CoreHandler implements HttpHandler {
    private final ObjectMapper objectMapper;
    private final FileStore fileStore;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    CoreHandler(FileStore fileStore, ObjectMapper objectMapper) {
      this.objectMapper = Objects.requireNonNull(objectMapper);
      this.fileStore = Objects.requireNonNull(fileStore);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {

      String relativePath = exchange.getRelativePath()
                                    .substring(1); // omit leading slash

      Path requestedFile;
      try {
        requestedFile = fileStore.resultsDirectory().resolve(relativePath);
      } catch (InvalidPathException ignored) {
        exchange.setStatusCode(NOT_FOUND);
        return;
      }

      if (!requestedFile.equals(requestedFile.normalize())
          || !requestedFile.startsWith(fileStore.resultsDirectory())) {
        exchange.setStatusCode(NOT_FOUND);
        return;
      }

      if (!Files.isRegularFile(requestedFile)) {
        exchange.setStatusCode(NOT_FOUND);
        return;
      }

      Results results;
      switch (MoreFiles.getFileExtension(requestedFile)) {
        case "json":
          try {
            results = objectMapper.readValue(requestedFile.toFile(), Results.class);
          } catch (IOException e) {
            logger.warn("Exception reading json file {}", requestedFile, e);
            exchange.setStatusCode(BAD_REQUEST);
            return;
          }
          break;

        case "zip":
          try {
            results =
                ZipFiles.readZipEntry(
                    /* zipFile= */ requestedFile,
                    /* entryPath= */ "results.json",
                    /* entryReader= */ inputStream ->
                                           objectMapper.readValue(inputStream,
                                                                  Results.class));

          } catch (IOException e) {
            logger.warn("Error reading zip file {}", requestedFile, e);
            exchange.setStatusCode(BAD_REQUEST);
            return;
          }
          if (results == null) {
            logger.warn("No results.json in zip file {}", requestedFile);
            exchange.setStatusCode(BAD_REQUEST);
            return;
          }
          break;

        default:
          logger.warn("Unable to handle file {}", requestedFile);
          exchange.setStatusCode(BAD_REQUEST);
          return;
      }

      var exportedView =
          new Results.TfbWebsiteView(
              /* name= */ results.name,
              /* completionTime= */ results.completionTime,
              /* duration= */ results.duration,
              /* queryIntervals= */ results.queryIntervals,
              /* concurrencyLevels= */ results.concurrencyLevels,
              /* rawData= */ results.rawData,
              /* failed= */ results.failed);

      String json = objectMapper.writeValueAsString(exportedView);
      exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_UTF_8.toString());
      exchange.getResponseSender().send(json);
    }
  }
}
