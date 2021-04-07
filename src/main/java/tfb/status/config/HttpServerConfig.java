package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import jakarta.inject.Singleton;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The configuration for the HTTP server.
 *
 * @param host The hostname for the HTTP server.  The HTTP server accepts
 *        incoming HTTP requests matching this hostname only.
 * @param port The port number for the HTTP server.  The HTTP server listens for
 *        incoming HTTP requests on this port only.  If this value is zero, then
 *        the HTTP server listens on an ephemeral port.  The ephemeral port is
 *        dynamically assigned to the HTTP server by the host system as the HTTP
 *        server is started.  HTTP clients are responsible for determining the
 *        ephemeral port number of the server somehow.  The algorithm for doing
 *        so is not specified by this class.
 * @param gracefulShutdownTimeoutMillis The maximum number of milliseconds the
 *        HTTP server may spend in "graceful shutdown".  During graceful
 *        shutdown, new incoming HTTP requests are rejected with {@code 503
 *        Service Unavailable} and in-progress HTTP requests are allowed this
 *        amount of time complete.  Then, after this amount of time has passed
 *        or after all the in-progress HTTP requests have completed &mdash;
 *        whichever comes first &mdash; {@linkplain
 *        #forcefulShutdownTimeoutMillis() "forceful shutdown"} will begin.
 * @param forcefulShutdownTimeoutMillis The maximum number of milliseconds the
 *        HTTP server may spend in "forceful shutdown", which occurs after
 *        {@linkplain #gracefulShutdownTimeoutMillis() "graceful shutdown"}.
 *        During forceful shutdown, the underlying I/O channels used by the HTTP
 *        server are forcefully closed, but request-handling threads are not
 *        interrupted.  Therefore, no new HTTP requests are accepted and no
 *        in-progress HTTP requests will complete successfully, but there is no
 *        guarantee that the request-handling threads will come to a natural
 *        stop.  If any of those threads haven't stopped after this amount of
 *        time, this application will proceed with shutting down all components
 *        anyway, and so the behavior of those threads is undefined.
 * @param keyStore The key store having the certificate for the HTTP server,
 *        enabling HTTPS, or {@code null} if the server is using unencrypted
 *        HTTP.  See {@link KeyStore}.
 */
@Immutable
@Singleton
public record HttpServerConfig(String host,
                               int port,
                               int gracefulShutdownTimeoutMillis,
                               int forcefulShutdownTimeoutMillis,
                               @Nullable KeyStore keyStore) {

  public HttpServerConfig {
    Objects.requireNonNull(host);
  }

  @JsonCreator
  public static HttpServerConfig create(
      @JsonProperty(value = "host", required = false)
      @Nullable String host,

      @JsonProperty(value = "port", required = false)
      @Nullable Integer port,

      @JsonProperty(value = "gracefulShutdownTimeoutMillis", required = false)
      @Nullable Integer gracefulShutdownTimeoutMillis,

      @JsonProperty(value = "forcefulShutdownTimeoutMillis", required = false)
      @Nullable Integer forcefulShutdownTimeoutMillis,

      @JsonProperty(value = "keyStore", required = false)
      @Nullable KeyStore keyStore) {

    return new HttpServerConfig(
        /* host= */
        Objects.requireNonNullElse(host, DEFAULT_HOST),

        /* port= */
        Objects.requireNonNullElse(port, DEFAULT_PORT),

        /* gracefulShutdownTimeoutMillis= */
        Objects.requireNonNullElse(
            gracefulShutdownTimeoutMillis,
            DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT_MILLIS),

        /* forcefulShutdownTimeoutMillis= */
        Objects.requireNonNullElse(
            forcefulShutdownTimeoutMillis,
            DEFAULT_FORCEFUL_SHUTDOWN_TIMEOUT_MILLIS),

        /* keyStore= */
        keyStore);
  }

  public static HttpServerConfig defaultConfig() {
    return create(null, null, null, null, null);
  }

  private static final String DEFAULT_HOST = "0.0.0.0";
  private static final int DEFAULT_PORT = 80;
  private static final int DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT_MILLIS = 2_000;
  private static final int DEFAULT_FORCEFUL_SHUTDOWN_TIMEOUT_MILLIS = 1_000;

  /**
   * The key store for the HTTP server.
   *
   * @param path The path to the key store file on the file system.
   * @param password The password for the key store.
   */
  @Immutable
  public record KeyStore(String path, String password) {

    public KeyStore {
      Objects.requireNonNull(path);
      Objects.requireNonNull(password);
    }

    @JsonCreator
    public static KeyStore create(
        @JsonProperty(value = "path", required = true)
        String path,

        @JsonProperty(value = "password", required = true)
        String password) {

      return new KeyStore(path, password);
    }
  }
}
