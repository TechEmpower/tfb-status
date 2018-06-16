package tfb.status.handler;

import static io.undertow.util.Headers.LOCATION;
import static io.undertow.util.Methods.POST;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static io.undertow.util.StatusCodes.SEE_OTHER;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.MoreFiles;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import io.undertow.server.handlers.form.EagerFormParsingHandler;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.FileStoreConfig;
import tfb.status.service.Authenticator;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.view.AttributeLookup;

/**
 * Handles requests to replace the content of the tfb_lookup.json file on disk.
 */
@Singleton
public final class SaveAttributesHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public SaveAttributesHandler(FileStoreConfig fileStoreConfig,
                               Authenticator authenticator,
                               ObjectMapper objectMapper) {

    HttpHandler handler = new CoreHandler(fileStoreConfig, objectMapper);

    handler = new MethodHandler().addMethod(POST, handler);
    handler = new DisableCacheHandler(handler);
    handler = new EagerFormParsingHandler().setNext(handler);
    handler = authenticator.newRequiredAuthHandler(handler);

    delegate = handler;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }

  private static final class CoreHandler implements HttpHandler {
    private final ObjectMapper objectMapper;
    private final Path attributesDirectory;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    CoreHandler(FileStoreConfig fileStoreConfig,
                ObjectMapper objectMapper) {

      this.attributesDirectory =  Paths.get(fileStoreConfig.attributesDirectory);
      this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      FormData form = exchange.getAttachment(FormDataParser.FORM_DATA);
      if (form == null) {
        logger.warn("Unable to parse the request form data");
        exchange.setStatusCode(BAD_REQUEST);
        return;
      }

      FormData.FormValue lookupField = form.getFirst("lookupjson");
      if (lookupField == null) {
        logger.warn("Missing required form field: lookupjson");
        exchange.setStatusCode(BAD_REQUEST);
        return;
      }

      String lookupJson = lookupField.getValue();

      AttributeLookup lookup;
      try {
        lookup = objectMapper.readValue(lookupJson, AttributeLookup.class);
      } catch (IOException e) {
        logger.warn("Unable to parse the updated tfb_lookup.json", e);
        exchange.setStatusCode(BAD_REQUEST);
        return;
      }

      Path tempFile = Files.createTempFile("TFB_lookup_upload", ".json");
      try {
        objectMapper.writeValue(tempFile.toFile(), lookup);
      } catch (IOException e) {
        logger.warn("Unable to save tfb_lookup.json", e);
        Files.delete(tempFile);
        exchange.setStatusCode(BAD_REQUEST);
        return;
      }

      Path lookupFile = attributesDirectory.resolve("tfb_lookup.json");
      MoreFiles.createParentDirectories(lookupFile);
      Files.move(
          /* source= */ tempFile,
          /* target= */ lookupFile,
          /* options= */ REPLACE_EXISTING);

      exchange.getResponseHeaders().put(LOCATION, "/");
      exchange.setStatusCode(SEE_OTHER);
    }
  }
}
