package tfb.status.handler;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.common.net.MediaType.ANY_TEXT_TYPE;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.StatusCodes.INTERNAL_SERVER_ERROR;
import static io.undertow.util.StatusCodes.NOT_FOUND;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static tfb.status.undertow.extensions.RequestValues.pathParameter;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.MoreFiles;
import com.google.common.net.MediaType;
import com.google.common.primitives.Booleans;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.handler.routing.DisableCache;
import tfb.status.handler.routing.Route;
import tfb.status.handler.routing.SetHeader;
import tfb.status.service.FileStore;
import tfb.status.service.MustacheRenderer;
import tfb.status.util.FileUtils;
import tfb.status.util.ZipFiles;
import tfb.status.view.UnzippedDirectoryView;
import tfb.status.view.UnzippedDirectoryView.FileView;

/**
 * Handles requests to extract files from within results.zip files.
 */
@Singleton
@Route(method = "GET", path = "/unzip/{zipFile}")
@Route(method = "GET", path = "/unzip/{zipFile}/{entrySubPath:.+}")
@DisableCache
// This endpoint is used by the TFB website when rendering results by uuid.
// Specifically, the TFB website uses this endpoint to fetch the
// test_metadata.json file associated with a given set of results.  This is only
// necessary for historic results, since the test metadata was added to the
// results themselves in January 2020.
@SetHeader(name = ACCESS_CONTROL_ALLOW_ORIGIN, value = "*")
public final class UnzipResultsHandler implements HttpHandler {
  private final FileStore fileStore;
  private final MustacheRenderer mustacheRenderer;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Inject
  public UnzipResultsHandler(FileStore fileStore,
                             MustacheRenderer mustacheRenderer) {

    this.fileStore = Objects.requireNonNull(fileStore);
    this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws IOException {
    String zipFileName = pathParameter(exchange, "zipFile").orElseThrow();
    String entrySubPath = pathParameter(exchange, "entrySubPath").orElse(null);

    Path zipFile =
        FileUtils.resolveChildPath(
            fileStore.resultsDirectory(),
            zipFileName);

    if (zipFile == null
        || !Files.isRegularFile(zipFile)
        || !MoreFiles.getFileExtension(zipFile).equals("zip")) {
      exchange.setStatusCode(NOT_FOUND);
      return;
    }

    ZipFiles.findZipEntry(
        /* zipFile= */
        zipFile,

        /* entryPath= */
        (entrySubPath == null) ? "" : entrySubPath,

        /* ifPresent= */
        (Path zipEntry) -> {

          if (Files.isRegularFile(zipEntry)) {
            MediaType mediaType = guessMediaType(zipEntry);

            if (mediaType != null)
              exchange.getResponseHeaders().put(CONTENT_TYPE,
                                                mediaType.toString());

            Files.copy(zipEntry, exchange.getOutputStream());
          }

          else if (Files.isDirectory(zipEntry)) {
            var path = new ArrayList<String>();
            path.add(zipFileName);

            if (entrySubPath != null)
              for (String part : Splitter.on('/').split(entrySubPath))
                path.add(part);

            var breadcrumbs = new ArrayList<FileView>();
            for (int i = 1; i <= path.size(); i++) {
              List<String> directoryPath = path.subList(0, i);
              breadcrumbs.add(
                  new FileView(
                      /* fileName= */ directoryPath.get(directoryPath.size() - 1),
                      /* fullPath= */ Joiner.on('/').join(directoryPath),
                      /* size= */ null,
                      /* isDirectory= */ true,
                      /* isSelected= */ i == path.size()));
            }

            var children = new ArrayList<FileView>();

            for (Path file : MoreFiles.listFiles(zipEntry)) {
              BasicFileAttributes attributes =
                  Files.readAttributes(file, BasicFileAttributes.class);

              String fullPath =
                  zipFile.getFileName().toString()
                      + "/"
                      + Joiner.on('/').join(file);

              String size =
                  attributes.isRegularFile()
                      ? fileSizeToString(attributes.size(), /* si= */ true)
                      : null;

              children.add(
                  new FileView(
                      /* fileName= */ file.getFileName().toString(),
                      /* fullPath= */ fullPath,
                      /* size= */ size,
                      /* isDirectory= */ attributes.isDirectory(),
                      /* isSelected= */ false));
            }

            Comparator<FileView> directoriesFirst =
                comparing(file -> file.isDirectory,
                          Booleans.trueFirst());

            Comparator<FileView> byFileName =
                comparing(file -> file.fileName,
                          String.CASE_INSENSITIVE_ORDER);

            children.sort(directoriesFirst.thenComparing(byFileName));

            var unzippedDirectoryView =
                new UnzippedDirectoryView(
                    /* breadcrumbs= */ ImmutableList.copyOf(breadcrumbs),
                    /* children= */ ImmutableList.copyOf(children));

            String html =
                mustacheRenderer.render("unzipped-directory.mustache",
                                        unzippedDirectoryView);

            exchange.getResponseHeaders().put(
                CONTENT_TYPE,
                HTML_UTF_8.toString());

            exchange.getResponseSender().send(html, UTF_8);
          }

          else {
            logger.warn(
                "Cannot unzip a zip entry that is neither a file nor a "
                    + "directory, zip file = {}, zip entry = {}",
                zipFile,
                zipEntry);

            exchange.setStatusCode(INTERNAL_SERVER_ERROR);
          }

        },

        /* ifAbsent= */
        () -> exchange.setStatusCode(NOT_FOUND));
  }

  /**
   * Guesses the media type of the given file.  Returns {@code null} if there is
   * not a good guess.
   *
   * @throws IOException if an I/O error occurs while examining the file
   */
  private static @Nullable MediaType guessMediaType(Path file)
      throws IOException {

    String extension = MoreFiles.getFileExtension(file);

    // TFB itself generates plaintext .log files.
    if (extension.equals("log"))
      return PLAIN_TEXT_UTF_8;

    String mediaTypeString = Files.probeContentType(file);
    if (mediaTypeString == null)
      return null;

    MediaType mediaType = MediaType.parse(mediaTypeString);
    if (mediaType.charset().isPresent() || !isTextType(mediaType))
      return mediaType;

    return mediaType.withCharset(UTF_8);
  }

  private static boolean isTextType(MediaType mediaType) {
    for (MediaType textType : KNOWN_TEXT_TYPES)
      if (mediaType.is(textType))
        return true;

    return false;
  }

  private static final ImmutableSet<MediaType> KNOWN_TEXT_TYPES =
      ImmutableSet.of(
          ANY_TEXT_TYPE,
          MediaType.create("application", "javascript"));

  // https://stackoverflow.com/a/3758880/359008
  private static String fileSizeToString(long bytes, boolean si) {
    int unit = si ? 1000 : 1024;
    if (bytes < unit) return bytes + " B";
    int exp = (int) (Math.log(bytes) / Math.log(unit));
    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
    return String.format(Locale.ROOT, "%.1f %sB", bytes / Math.pow(unit, exp), pre);
  }
}
