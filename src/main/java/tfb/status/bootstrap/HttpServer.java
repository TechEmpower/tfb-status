package tfb.status.bootstrap;

import com.google.common.io.MoreFiles;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.net.ssl.SSLContext;
import org.glassfish.hk2.api.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.HttpServerConfig;
import tfb.status.handler.routing.AllPaths;
import tfb.status.util.KeyStores;

/**
 * The HTTP server for this application.
 *
 * <p>This server does not start automatically.  Call {@link #start()} to begin
 * listening for incoming HTTP requests.
 */
@Singleton
public final class HttpServer implements PreDestroy {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final String serverInfo;

  @GuardedBy("this") private final Undertow server;
  @GuardedBy("this") private boolean isRunning;

  @Inject
  public HttpServer(HttpServerConfig config,
                    @AllPaths HttpHandler handler,
                    FileSystem fileSystem) {

    Objects.requireNonNull(config);
    Objects.requireNonNull(handler);
    Objects.requireNonNull(fileSystem);

    Undertow.Builder builder = Undertow.builder();
    builder.setHandler(handler);
    builder.setServerOption(UndertowOptions.RECORD_REQUEST_START_TIME, true);

    if (config.keyStore == null)
      builder.addHttpListener(config.port, config.host);

    else {
      Path keyStoreFile = fileSystem.getPath(config.keyStore.path);

      SSLContext sslContext =
          KeyStores.readServerSslContext(
              /* keyStoreBytes= */ MoreFiles.asByteSource(keyStoreFile),
              /* password= */ config.keyStore.password.toCharArray());

      builder.addHttpsListener(config.port, config.host, sslContext);
      builder.setServerOption(UndertowOptions.ENABLE_HTTP2, true);
    }

    serverInfo =
        "host=" + config.host
            + ", port=" + config.port
            + ", encrypted=" + (config.keyStore != null);

    server = builder.build();
  }

  @Override
  public void preDestroy() {
    stop();
  }

  /**
   * Starts this server if it is currently stopped.
   */
  public synchronized void start() {
    if (isRunning) return;

    server.start();
    isRunning = true;
    logger.info("running [{}]", serverInfo);
  }

  /**
   * Stops this server if it is currently running.
   */
  public synchronized void stop() {
    if (!isRunning) return;

    server.stop();
    isRunning = false;
    logger.info("stopped [{}]", serverInfo);
  }
}
