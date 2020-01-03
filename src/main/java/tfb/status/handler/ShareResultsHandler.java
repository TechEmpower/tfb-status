package tfb.status.handler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;
import com.google.common.io.MoreFiles;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.service.FileStore;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.view.Results;
import tfb.status.view.ShareResultsJsonView;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.Methods.POST;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static io.undertow.util.StatusCodes.NOT_FOUND;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * A handler for uploading and viewing results.json files. This is intended for
 * anyone to use, and requires no authentication. POST fully formed and completed
 * results.json files to this handler in order to upload them. The JSON must conform
 * to {@link Results} such that it can deserialize without error, and must have a
 * non-empty {@link Results#testMetadata} array. Upon success, JSON is returned
 * that contains info about how to access the raw JSON and also visualize it on
 * the TechEmpower benchmarks site.
 */
@Singleton
@PrefixPath("/share-results")
public class ShareResultsHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public ShareResultsHandler(FileStore fileStore, ObjectMapper objectMapper) {
    HttpHandler uploadHandler = new DisableCacheHandler(
        new UploadHandler(fileStore, objectMapper));

    // The files never change, so do not disable caching.
    HttpHandler viewHandler = new ViewHandler(fileStore);

    HttpHandler handler = new MethodHandler()
        .addMethod(POST, uploadHandler)
        .addMethod(GET, viewHandler);

    handler = new SetHeaderHandler(handler, ACCESS_CONTROL_ALLOW_ORIGIN, "*");

    delegate = handler;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }

  private static final String TFB_STATUS_ORIGIN = "https://tfb-status.techempower.com";
  private static final String TE_WEB_ORIGIN = "https://www.techempower.com";

  /**
   * Handle POST upload requests. Every valid request gets placed in the share
   * directory as a new file.
   */
  private static class UploadHandler implements HttpHandler {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final FileStore fileStore;
    private final ObjectMapper objectMapper;

    UploadHandler(FileStore fileStore, ObjectMapper objectMapper) {
      this.fileStore = Objects.requireNonNull(fileStore);
      this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      if (!exchange.getRelativePath().isEmpty()
          && !exchange.getRelativePath().equals("/")) {
        exchange.setStatusCode(NOT_FOUND);
        return;
      }

      Path tempFile = Files.createTempFile(/* prefix= */ "TFB_Share_Upload",
                                           /* suffix= */ ".json");

      try (WritableByteChannel out =
               Files.newByteChannel(tempFile, WRITE, APPEND)) {
        // TODO: Is this safe?
        ReadableByteChannel in = exchange.getRequestChannel();
        ByteStreams.copy(in, out);
      }

      if (!validateNewFile(tempFile)) {
        Files.delete(tempFile);
        exchange.setStatusCode(BAD_REQUEST);
        return;
      }

      String shareId = UUID.randomUUID().toString();
      String fileName = shareId + ".json";
      Path permanentFile = fileStore.shareDirectory().resolve(fileName);

      MoreFiles.createParentDirectories(permanentFile);

      Files.move(
          /* source= */ tempFile,
          /* target= */ permanentFile,
          /* options= */ REPLACE_EXISTING);

      String resultsUrl = TFB_STATUS_ORIGIN + "/share-results/" + fileName;
      String visualizeResultsUrl = TE_WEB_ORIGIN + "/benchmarks/#section=test&shareid="
          + URLEncoder.encode(shareId, UTF_8);

      ShareResultsJsonView shareResultsJsonView = new ShareResultsJsonView(
          /* fileName= */ fileName,
          /* resultsUrl= */ resultsUrl,
          /* visualizeResultsUrl= */ visualizeResultsUrl);

      String json = objectMapper.writeValueAsString(shareResultsJsonView);
      exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_UTF_8.toString());
      exchange.getResponseSender().send(json, UTF_8);
    }

    /**
     * Return true if the json file successfully deserializes to {@link Results}
     * and has non-empty {@link Results#testMetadata}.
     */
    private boolean validateNewFile(Path newJsonFile) {
      Objects.requireNonNull(newJsonFile);

      try (InputStream inputStream = Files.newInputStream(newJsonFile)) {
        Results results = objectMapper.readValue(inputStream, Results.class);

        if (results.testMetadata != null && !results.testMetadata.isEmpty()) {
          return true;
        }
      } catch (IOException e) {
        logger.warn("Exception validating json file {}", newJsonFile, e);
      }

      return false;
    }
  }

  /**
   * Handles GET requests for view files from the share directory.
   */
  private static class ViewHandler implements HttpHandler {
    private final FileStore fileStore;

    private ViewHandler(FileStore fileStore) {
      this.fileStore = Objects.requireNonNull(fileStore);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      if (exchange.getRelativePath().isEmpty()) {
        exchange.setStatusCode(NOT_FOUND);
        return;
      }

      // omit leading slash
      String relativePath = exchange.getRelativePath().substring(1);

      Path requestedFile;
      try {
        requestedFile = fileStore.shareDirectory().resolve(relativePath);
      } catch (InvalidPathException ignored) {
        exchange.setStatusCode(NOT_FOUND);
        return;
      }

      if (!requestedFile.equals(requestedFile.normalize())
          || !requestedFile.startsWith(fileStore.shareDirectory())) {
        exchange.setStatusCode(NOT_FOUND);
        return;
      }

      if (!Files.isRegularFile(requestedFile)
          || !MoreFiles.getFileExtension(requestedFile).equals("json")) {
        exchange.setStatusCode(NOT_FOUND);
        return;
      }

      exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_UTF_8.toString());
      try (ReadableByteChannel in = Files.newByteChannel(requestedFile, READ)) {
        WritableByteChannel out = exchange.getResponseChannel();
        ByteStreams.copy(in, out);
      }
    }
  }
}
