package tfb.status.bootstrap;

import com.google.common.io.MoreFiles;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.HttpServerConfig;
import tfb.status.handler.RootHandler;

/**
 * The HTTP server for this application.
 */
@Singleton
public final class HttpServer {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final String serverInfo;

  @GuardedBy("this") private final Undertow server;
  @GuardedBy("this") private boolean isRunning;

  @Inject
  public HttpServer(HttpServerConfig config, RootHandler rootHandler) {
    Objects.requireNonNull(config);
    Objects.requireNonNull(rootHandler);

    Undertow.Builder builder = Undertow.builder();
    builder.setHandler(rootHandler);

    if (config.keyStore == null)
      builder.addHttpListener(config.port, config.host);

    else {
      Path keyStoreFile = Paths.get(config.keyStore.path);

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

  /**
   * Starts this server if it is currently stopped.
   */
  @PostConstruct
  public synchronized void start() {
    if (isRunning) return;

    server.start();
    isRunning = true;
    logger.info("running [{}]", serverInfo);
  }

  /**
   * Stops this server if it is currently running.
   */
  @PreDestroy
  public synchronized void stop() {
    if (!isRunning) return;

    server.stop();
    isRunning = false;
    logger.info("stopped [{}]", serverInfo);
  }
}
