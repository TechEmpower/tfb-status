package tfb.status.handler;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.undertow.util.StatusCodes.NOT_FOUND;
import static tfb.status.undertow.extensions.RequestValues.pathParameter;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.handler.routing.Route;
import tfb.status.handler.routing.SetHeader;
import tfb.status.service.FileStore;
import tfb.status.util.FileUtils;

/**
 * Handles requests to download results.json files that were shared by users.
 */
@Singleton
@Route(
    method = "GET",
    path = "/share/download/{shareIdDotJson}",
    produces = "application/json")
// This endpoint is used by the TFB website when rendering results by share id.
@SetHeader(name = ACCESS_CONTROL_ALLOW_ORIGIN, value = "*")
public final class ShareDownloadHandler implements HttpHandler {
  private final FileStore fileStore;

  @Inject
  public ShareDownloadHandler(FileStore fileStore) {
    this.fileStore = Objects.requireNonNull(fileStore);
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {

    String shareIdDotJson =
        pathParameter(exchange, "shareIdDotJson").orElseThrow();

    Matcher matcher = SHARE_ID_DOT_JSON_PATTERN.matcher(shareIdDotJson);
    if (!matcher.matches()) {
      exchange.setStatusCode(NOT_FOUND);
      return;
    }

    String shareId = matcher.group("shareId");

    Path sharedFile =
        FileUtils.resolveChildPath(
            /* directory= */ fileStore.shareDirectory(),
            /* fileName= */ shareId + ".json.gz");

    if (sharedFile == null || !Files.isRegularFile(sharedFile)) {
      exchange.setStatusCode(NOT_FOUND);
      return;
    }

    // We could parse the "Accept-Encoding" request header to determine if the
    // client supports gzip, and if so, add "Content-Encoding: gzip" to the
    // response headers and transfer the file as is.  For now, we don't think
    // that feature would be worth the added complexity.
    try (InputStream inputStream = Files.newInputStream(sharedFile);
         GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
      gzipInputStream.transferTo(exchange.getOutputStream());
    }
  }

  // Matches "6f221937-b8e5-4b22-a52d-020d2538fa64.json", for example.
  private static final Pattern SHARE_ID_DOT_JSON_PATTERN =
      Pattern.compile("^(?<shareId>[\\w-]+)\\.json$");
}
