package tfb.status.testlib;

import com.google.common.io.MoreFiles;
import jakarta.inject.Singleton;
import java.net.http.HttpClient;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import tfb.status.config.HttpServerConfig;
import org.glassfish.hk2.extras.provides.Provides;
import tfb.status.service.HttpServer;
import tfb.status.util.KeyStores;

/**
 * Provides the {@link HttpClient} used by this application during tests.
 */
final class HttpClientFactory {
  private HttpClientFactory() {
    throw new AssertionError("This class cannot be instantiated");
  }

  @Provides
  @Singleton
  public static HttpClient httpClient(HttpServerConfig config,
                                      FileSystem fileSystem,
                                      HttpServer httpServer) {

    HttpClient.Builder builder = HttpClient.newBuilder();
    builder.connectTimeout(Duration.ofSeconds(10));

    if (config.keyStore() != null) {
      Path keyStoreFile = fileSystem.getPath(config.keyStore().path());

      SSLContext sslContext =
          KeyStores.readClientSslContext(
              /* keyStoreBytes= */ MoreFiles.asByteSource(keyStoreFile),
              /* password= */ config.keyStore().password().toCharArray());

      builder.sslContext(sslContext);
    }

    HttpClient client = builder.build();

    // Ensure that the HTTP server is running before the client is used.
    httpServer.start();

    return client;
  }
}
