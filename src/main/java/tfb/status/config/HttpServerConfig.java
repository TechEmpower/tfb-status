package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import jakarta.inject.Singleton;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The configuration for the HTTP server.
 */
@Immutable
@Singleton
public final class HttpServerConfig {
  /**
   * The hostname for the HTTP server.  The HTTP server accepts incoming HTTP
   * requests matching this hostname only.
   */
  public final String host;

  /**
   * The port number for the HTTP server.  The HTTP server listens for incoming
   * HTTP requests on this port only.
   *
   * <p>If this value is zero, then the HTTP server listens on an ephemeral
   * port.  The ephemeral port is dynamically assigned to the HTTP server by the
   * host system as the HTTP server is started.  HTTP clients are responsible
   * for determining the ephemeral port number of the server somehow.  The
   * algorithm for doing so is not specified by this class.
   */
  public final int port;

  /**
   * The maximum number of milliseconds the HTTP server may spend in "graceful
   * shutdown".
   *
   * <p>During graceful shutdown, new incoming HTTP requests are rejected with
   * {@code 503 Service Unavailable} and in-progress HTTP requests are allowed
   * this amount of time complete.  Then, after this amount of time has passed
   * or after all the in-progress HTTP requests have completed &mdash; whichever
   * comes first &mdash; {@linkplain #forcefulShutdownTimeoutMillis "forceful
   * shutdown"} will begin.
   */
  public final int gracefulShutdownTimeoutMillis;

  /**
   * The maximum number of milliseconds the HTTP server may spend in "forceful
   * shutdown", which occurs after {@linkplain #gracefulShutdownTimeoutMillis
   * "graceful shutdown"}.
   *
   * <p>During forceful shutdown, the underlying I/O channels used by the HTTP
   * server are forcefully closed, but request-handling threads are not
   * interrupted.  Therefore, no new HTTP requests are accepted and no
   * in-progress HTTP requests will complete successfully, but there is no
   * guarantee that the request-handling threads will come to a natural stop.
   * If any of those threads haven't stopped after this amount of time, this
   * application will proceed with shutting down all components anyway, and so
   * the behavior of those threads is undefined.
   */
  public final int forcefulShutdownTimeoutMillis;

  /**
   * The key store having the certificate for the HTTP server, enabling HTTPS,
   * or {@code null} if the server is using unencrypted HTTP.  See {@link
   * KeyStore}.
   */
  public final @Nullable KeyStore keyStore;

  public HttpServerConfig(String host,
                          int port,
                          int gracefulShutdownTimeoutMillis,
                          int forcefulShutdownTimeoutMillis,
                          @Nullable KeyStore keyStore) {
    this.host = Objects.requireNonNull(host);
    this.port = port;
    this.gracefulShutdownTimeoutMillis = gracefulShutdownTimeoutMillis;
    this.forcefulShutdownTimeoutMillis = forcefulShutdownTimeoutMillis;
    this.keyStore = keyStore;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    } else if (!(object instanceof HttpServerConfig)) {
      return false;
    } else {
      HttpServerConfig that = (HttpServerConfig) object;
      return this.host.equals(that.host)
          && this.port == that.port
          && this.gracefulShutdownTimeoutMillis == that.gracefulShutdownTimeoutMillis
          && this.forcefulShutdownTimeoutMillis == that.forcefulShutdownTimeoutMillis
          && Objects.equals(this.keyStore, that.keyStore);
    }
  }

  @Override
  public int hashCode() {
    int hash = 1;
    hash = 31 * hash + host.hashCode();
    hash = 31 * hash + Integer.hashCode(port);
    hash = 31 * hash + Integer.hashCode(gracefulShutdownTimeoutMillis);
    hash = 31 * hash + Integer.hashCode(forcefulShutdownTimeoutMillis);
    hash = 31 * hash + Objects.hashCode(keyStore);
    return hash;
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
   */
  @Immutable
  public static final class KeyStore {
    /**
     * The path to the key store file on the file system.
     */
    public final String path;

    /**
     * The password for the key store.
     */
    public final String password;

    public KeyStore(String path, String password) {
      this.path = Objects.requireNonNull(path);
      this.password = Objects.requireNonNull(password);
    }

    @Override
    public boolean equals(@Nullable Object object) {
      if (object == this) {
        return true;
      } else if (!(object instanceof KeyStore)) {
        return false;
      } else {
        KeyStore that = (KeyStore) object;
        return this.path.equals(that.path)
            && this.password.equals(that.password);
      }
    }

    @Override
    public int hashCode() {
      int hash = 1;
      hash = 31 * hash + path.hashCode();
      hash = 31 * hash + password.hashCode();
      return hash;
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
