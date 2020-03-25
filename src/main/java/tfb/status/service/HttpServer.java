package tfb.status.service;

import static io.undertow.UndertowOptions.ENABLE_HTTP2;
import static io.undertow.UndertowOptions.RECORD_REQUEST_START_TIME;
import static io.undertow.UndertowOptions.SHUTDOWN_TIMEOUT;

import com.google.common.io.MoreFiles;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.undertow.Undertow;
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
 * <p>This HTTP server does not start automatically.  Call {@link #start()} to
 * begin listening for incoming HTTP requests.
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
    builder.setServerOption(RECORD_REQUEST_START_TIME, true);

    // Without this shutdown timeout, stopping this HTTP server would block the
    // current thread until all in-progress HTTP requests complete naturally.
    // That would mean a single misbehaving HTTP request could delay shutdown
    // indefinitely.
    builder.setServerOption(
        SHUTDOWN_TIMEOUT,
        config.forcefulShutdownTimeoutMillis);

    if (config.keyStore == null)
      builder.addHttpListener(config.port, config.host);

    else {
      Path keyStoreFile = fileSystem.getPath(config.keyStore.path);

      SSLContext sslContext =
          KeyStores.readServerSslContext(
              /* keyStoreBytes= */ MoreFiles.asByteSource(keyStoreFile),
              /* password= */ config.keyStore.password.toCharArray());

      builder.addHttpsListener(config.port, config.host, sslContext);
      builder.setServerOption(ENABLE_HTTP2, true);
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
   * Starts this HTTP server if it is currently stopped.
   */
  public synchronized void start() {
    if (isRunning) return;

    server.start();
    isRunning = true;
    logger.info("running [{}]", serverInfo);
  }

  /**
   * Stops this HTTP server if it is currently running.
   *
   * <p>It is not necessarily the case that all HTTP request-handling threads
   * have stopped when this method returns.  This method will wait
   * {@link HttpServerConfig#forcefulShutdownTimeoutMillis} for the threads to
   * stop, but if some threads are still running after that amount of time, this
   * method will return anyway.
   */
  public synchronized void stop() {
    if (!isRunning) return;

    // Blocks this thread for up to SHUTDOWN_TIMEOUT milliseconds.
    server.stop();

    isRunning = false;
    logger.info("stopped [{}]", serverInfo);
  }
}
