package tfb.status.handler;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Headers.LOCATION;
import static io.undertow.util.Methods.POST;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static io.undertow.util.StatusCodes.CREATED;
import static io.undertow.util.StatusCodes.REQUEST_ENTITY_TOO_LARGE;
import static io.undertow.util.StatusCodes.SERVICE_UNAVAILABLE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE_NEW;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteStreams;
import com.google.common.io.MoreFiles;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.MessagingException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.ShareConfig;
import tfb.status.handler.routing.ExactPath;
import tfb.status.hk2.extensions.Provides;
import tfb.status.service.EmailSender;
import tfb.status.service.FileStore;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MediaTypeHandler;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.util.FileUtils;
import tfb.status.view.Results;
import tfb.status.view.ShareFailure;
import tfb.status.view.ShareSuccess;

/**
 * Handles requests from users to share results.json files.
 *
 * <p>This feature is available for anyone to use; no authentication is
 * required.
 *
 * <p>The {@code Content-Type} of each incoming request must be {@code
 * application/json} and the body of the request must be the content of the
 * results.json file.
 *
 * <p>The results.json file must conform to {@link Results}, meaning that it can
 * deserialize from JSON without error.  The results.json file must have
 * non-empty {@link Results#testMetadata}.
 *
 * <p>Upon a successful upload, the response body is JSON that describes how to
 * access the raw JSON and how to visualize it on the TFB website.
 */
@Singleton
public final class ShareUploadHandler implements HttpHandler {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final ShareConfig config;
  private final FileStore fileStore;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final EmailSender emailSender;

  @GuardedBy("emailTimeLock")
  private volatile @Nullable Instant previousEmailTime;
  private final Object emailTimeLock = new Object();

  @Inject
  public ShareUploadHandler(ShareConfig config,
                            FileStore fileStore,
                            ObjectMapper objectMapper,
                            Clock clock,
                            EmailSender emailSender) {

    this.config = Objects.requireNonNull(config);
    this.fileStore = Objects.requireNonNull(fileStore);
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.clock = Objects.requireNonNull(clock);
    this.emailSender = Objects.requireNonNull(emailSender);
  }

  @Provides
  @Singleton
  @ExactPath("/share/upload")
  public HttpHandler shareUploadHandler() {
    return HttpHandlers.chain(
        this,
        handler -> new MediaTypeHandler().addMediaType("application/json", handler),
        handler -> new MethodHandler().addMethod(POST, handler),
        handler -> new DisableCacheHandler(handler));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws IOException {
    ShareOutcome outcome = share(exchange.getInputStream());
    if (outcome.failure != null) {
      String json = objectMapper.writeValueAsString(outcome.failure);
      exchange.setStatusCode(statusCodeForFailure(outcome.failure.kind));
      exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_UTF_8.toString());
      exchange.getResponseSender().send(json, UTF_8);
      return;
    }

    Objects.requireNonNull(outcome.success);
    String json = objectMapper.writeValueAsString(outcome.success);
    exchange.setStatusCode(CREATED);
    exchange.getResponseHeaders().put(LOCATION, outcome.success.resultsUrl);
    exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_UTF_8.toString());
    exchange.getResponseSender().send(json, UTF_8);
  }

  /**
   * Accepts a new results.json file to be shared.
   *
   * <p>This method first validates the size requirements: that the given file
   * isn't too large, and that the share directory is not full.  This method
   * then validates the contents of the file, ensuring that it de-serializes to
   * a {@link Results} object successfully, and that it contains a non-empty
   * {@link Results#testMetadata}.
   *
   * @param resultsBytes the bytes of the results.json file to be shared
   * @return an object describing the success or failure of the call
   */
  private ShareOutcome share(InputStream resultsBytes) throws IOException {
    Objects.requireNonNull(resultsBytes);

    // We are only checking if the share directory is currently under its max
    // size, without the addition of the new file.  This reduces the complexity
    // and potentially wasted time of zipping the json file before checking if
    // it can fit in the share directory.  This is a fine compromise because it
    // means that at most the share directory will exceed the max size by just
    // one large results json file, and we will not accept further uploads after
    // that.
    long shareDirectorySize =
        FileUtils.directorySizeInBytes(fileStore.shareDirectory());

    if (shareDirectorySize >= config.maxDirectorySizeInBytes) {
      onShareDirectoryFull(
          config.maxDirectorySizeInBytes,
          shareDirectorySize);

      return new ShareOutcome(
          new ShareFailure(
              ShareFailure.Kind.SHARE_DIRECTORY_FULL,
              "Share uploads has reached max capacity."));
    }

    Path tempFile =
        Files.createTempFile(
            /* prefix= */ "TFB_Share_Upload",
            /* suffix= */ ".json");

    try {
      InputStream limitedBytes =
          ByteStreams.limit(
              resultsBytes,
              config.maxFileSizeInBytes + 1);

      long fileSize = Files.copy(limitedBytes, tempFile, REPLACE_EXISTING);

      if (fileSize > config.maxFileSizeInBytes)
        return new ShareOutcome(
            new ShareFailure(
                ShareFailure.Kind.FILE_TOO_LARGE,
                "Share uploads cannot exceed "
                    + config.maxFileSizeInBytes
                    + " bytes."));

      Results results;
      try (InputStream inputStream = Files.newInputStream(tempFile)) {
        results = objectMapper.readValue(inputStream, Results.class);
      } catch (JsonProcessingException e) {
        logger.info("Exception processing json file {}", tempFile, e);
        return new ShareOutcome(
            new ShareFailure(
                ShareFailure.Kind.INVALID_JSON,
                "Invalid results JSON"));
      }

      if (results.testMetadata == null || results.testMetadata.isEmpty())
        return new ShareOutcome(
            new ShareFailure(
                ShareFailure.Kind.MISSING_TEST_METADATA,
                "Results must contain non-empty test metadata"));

      String shareId = UUID.randomUUID().toString();
      String sharedFileName = shareId + ".json.gz";
      Path sharedFile = fileStore.shareDirectory().resolve(sharedFileName);
      MoreFiles.createParentDirectories(sharedFile);

      try (OutputStream outputStream =
               Files.newOutputStream(sharedFile, CREATE_NEW);

           GZIPOutputStream gzipOutputStream =
               new GZIPOutputStream(outputStream)) {

        Files.copy(tempFile, gzipOutputStream);
      }

      String resultsUrl =
          config.tfbStatusOrigin
              + "/share/download/"
              + URLEncoder.encode(shareId + ".json", UTF_8);

      String visualizeResultsUrl =
          config.tfbWebsiteOrigin
              + "/benchmarks/#section=test&shareid="
              + URLEncoder.encode(shareId, UTF_8);

      return new ShareOutcome(
          new ShareSuccess(
              /* shareId= */ shareId,
              /* resultsUrl= */ resultsUrl,
              /* visualizeResultsUrl= */ visualizeResultsUrl));

    } finally {
      Files.delete(tempFile);
    }
  }

  /**
   * An object indicating the success or failure of a call to {@link
   * #share(InputStream)}.  If the call was a success, then {@link #success} is
   * non-{@code null}.  Otherwise, {@link #failure} is non-{@code null}.
   */
  @Immutable
  private static final class ShareOutcome {
    final @Nullable ShareSuccess success;
    final @Nullable ShareFailure failure;

    ShareOutcome(ShareSuccess success) {
      this.success = Objects.requireNonNull(success);
      this.failure = null;
    }

    ShareOutcome(ShareFailure failure) {
      this.success = null;
      this.failure = Objects.requireNonNull(failure);
    }
  }

  /**
   * Returns the HTTP response status code appropriate for the specified kind of
   * failure.
   */
  private static int statusCodeForFailure(ShareFailure.Kind failureKind) {
    Objects.requireNonNull(failureKind);

    return switch (failureKind) {
      case MISSING_TEST_METADATA, INVALID_JSON ->
          BAD_REQUEST;

      case FILE_TOO_LARGE ->
          REQUEST_ENTITY_TOO_LARGE;

      case SHARE_DIRECTORY_FULL ->
          SERVICE_UNAVAILABLE;
    };
  }

  /**
   * Notifies the maintainers of this application that the share directory is
   * full.  This method may return without sending the email if a similar email
   * was sent too recently.
   *
   * @param capacityBytes the maximum size of the share directory
   * @param sizeBytes the current size of the share directory
   */
  private void onShareDirectoryFull(long capacityBytes, long sizeBytes) {
    synchronized (emailTimeLock) {
      Instant now = clock.instant();
      Instant previous = this.previousEmailTime;

      if (previous != null) {
        Instant nextEmailTime =
            previous.plusSeconds(config.minSecondsBetweenEmails);

        if (now.isBefore(nextEmailTime)) {
          logger.warn(
              "Suppressing email for full share directory, "
                  + "another email was sent for that account too recently, "
                  + "previous email time = {}, next possible email time = {}",
              previous,
              nextEmailTime);
          return;
        }
      }

      this.previousEmailTime = now;
    }

    String textContent =
        "Hello,"
            + "\n"
            + "\n"
            + "The share directory used for storing public uploads of results "
            + "files has reached capacity.  Please audit the directory and "
            + "delete old uploads or expand the configured capacity."
            + "\n"
            + "\n"
            + "Share directory capacity: " + capacityBytes + " bytes"
            + "\n"
            + "Share directory size:     " + sizeBytes + " bytes"
            + "\n"
            + "\n"
            + "-a robot";

    try {
      emailSender.sendEmail(
          /* subject= */ SHARE_DIRECTORY_FULL_SUBJECT,
          /* textContent= */ textContent,
          /* attachments= */ List.of());

    } catch (MessagingException e) {
      logger.warn("Error sending email for share directory full", e);
    }
  }

  @VisibleForTesting
  static final String SHARE_DIRECTORY_FULL_SUBJECT =
      "<tfb> <auto> Share directory full";
}
