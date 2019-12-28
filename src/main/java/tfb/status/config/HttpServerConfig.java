package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The configuration for the HTTP server.
 */
@Immutable
@Singleton
public final class HttpServerConfig {
  /**
   * The hostname for the server.  The server accepts incoming HTTP requests
   * matching this hostname only.
   */
  public final String host;

  /**
   * The port number for the server.  The server listens for incoming HTTP
   * requests on this port only.
   */
  public final int port;

  /**
   * The key store having the certificate for the server, enabling HTTPS, or
   * {@code null} if the server is using unencrypted HTTP.  See {@link
   * KeyStore}.
   */
  public final @Nullable KeyStore keyStore;

  public HttpServerConfig(String host, int port, @Nullable KeyStore keyStore) {
    this.host = Objects.requireNonNull(host);
    this.port = port;
    this.keyStore = keyStore;
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) {
      return true;
    } else if (!(object instanceof HttpServerConfig)) {
      return false;
    } else {
      HttpServerConfig that = (HttpServerConfig) object;
      return this.port == that.port
          && this.host.equals(that.host)
          && Objects.equals(this.keyStore, that.keyStore);
    }
  }

  @Override
  public int hashCode() {
    int hash = 1;
    hash = 31 * hash + host.hashCode();
    hash = 31 * hash + Integer.hashCode(port);
    hash = 31 * hash + Objects.hashCode(keyStore);
    return hash;
  }

  @JsonCreator
  public static HttpServerConfig create(
      @JsonProperty(value = "host", required = false)
      @Nullable String host,

      @JsonProperty(value = "port", required = false)
      @Nullable Integer port,

      @JsonProperty(value = "keyStore", required = false)
      @Nullable KeyStore keyStore) {

    return new HttpServerConfig(
        /* host= */
        Objects.requireNonNullElse(host, DEFAULT_HOST),

        /* port= */
        Objects.requireNonNullElse(port, DEFAULT_PORT),

        /* keyStore= */
        keyStore);
  }

  public static HttpServerConfig defaultConfig() {
    return create(null, null, null);
  }

  private static final String DEFAULT_HOST = "0.0.0.0";
  private static final int DEFAULT_PORT = 80;

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
    public boolean equals(Object object) {
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
