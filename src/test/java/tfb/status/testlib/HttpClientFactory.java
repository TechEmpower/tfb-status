package tfb.status.testlib;

import com.google.common.io.MoreFiles;
import java.net.http.HttpClient;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.net.ssl.SSLContext;
import org.glassfish.hk2.api.Factory;
import tfb.status.bootstrap.HttpServer;
import tfb.status.config.HttpServerConfig;
import tfb.status.util.KeyStores;

/**
 * Provides the {@link HttpClient} used by this application during tests.
 */
@Singleton
final class HttpClientFactory implements Factory<HttpClient> {
  private final HttpServerConfig config;
  private final FileSystem fileSystem;
  private final Provider<HttpServer> httpServerProvider;

  @Inject
  public HttpClientFactory(HttpServerConfig config,
                           FileSystem fileSystem,
                           Provider<HttpServer> httpServerProvider) {

    this.config = Objects.requireNonNull(config);
    this.fileSystem = Objects.requireNonNull(fileSystem);
    this.httpServerProvider = Objects.requireNonNull(httpServerProvider);
  }

  @Override
  @Singleton
  public HttpClient provide() {
    HttpClient.Builder builder = HttpClient.newBuilder();

    if (config.keyStore != null) {
      Path keyStoreFile = fileSystem.getPath(config.keyStore.path);

      SSLContext sslContext =
          KeyStores.readClientSslContext(
              /* keyStoreBytes= */ MoreFiles.asByteSource(keyStoreFile),
              /* password= */ config.keyStore.password.toCharArray());

      builder.sslContext(sslContext);
    }

    HttpClient client = builder.build();

    // Ensure that the HTTP server is running before the client is used.
    HttpServer server = httpServerProvider.get();
    server.start();

    return client;
  }

  @Override
  public void dispose(HttpClient instance) {
    // No cleanup required.
  }
}
