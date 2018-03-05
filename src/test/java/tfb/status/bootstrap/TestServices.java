package tfb.status.bootstrap;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Ticker;
import com.google.common.io.ByteSource;
import com.google.common.io.MoreFiles;
import com.google.common.io.Resources;
import com.icegreen.greenmail.store.FolderException;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.Base64;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.mail.internet.MimeMessage;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.UriBuilder;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.jersey.media.sse.SseFeature;
import org.threeten.extra.MutableClock;
import tfb.status.config.ApplicationConfig;
import tfb.status.config.EmailConfig;
import tfb.status.config.HttpServerConfig;
import tfb.status.util.MutableTicker;

public final class TestServices {
  private final ApplicationConfig config;
  private final MutableClock clock;
  private final MutableTicker ticker;
  private final ServiceLocator serviceLocator;
  private final Client httpClient;
  @Nullable private final GreenMail mailServer;

  public TestServices() {
    this.config = newApplicationConfig();
    this.clock = MutableClock.epochUTC();
    this.ticker = new MutableTicker();
    this.serviceLocator = Services.newServiceLocator(config, clock, ticker);
    this.httpClient = newHttpClient(config.http);

    if (config.email == null)
      mailServer = null;
    else {
      mailServer = newMailServer(config.email);
      mailServer.start();
    }

    HttpServer httpServer = serviceLocator.getService(HttpServer.class);
    httpServer.start();
  }

  /**
   * Shuts down all services.
   */
  public void shutdown() {
    try {
      httpClient.close();
    } finally {
      try {
        serviceLocator.shutdown();
      } finally {
        if (mailServer != null) {
          mailServer.stop();
        }
      }
    }
  }

  /**
   * The {@link ApplicationConfig} used by every service that is configurable.
   */
  public ApplicationConfig config() {
    return config;
  }

  /**
   * The {@link Clock} used by every service that needs to read the current wall
   * clock time.  This clock can be adjusted manually.
   */
  public MutableClock clock() {
    return clock;
  }

  /**
   * The {@link Ticker} used by every service that needs to measure elapsed
   * time.  This ticker can be adjusted manually.
   */
  public MutableTicker ticker() {
    return ticker;
  }

  /**
   * The {@link ServiceLocator} capable of producing all services in the
   * application.
   *
   * @see Services#newServiceLocator(ApplicationConfig, Clock, Ticker)
   */
  public ServiceLocator serviceLocator() {
    return serviceLocator;
  }

  /**
   * Returns the only email message that has been received by the email server
   * since the last time this method was invoked.
   *
   * @throws IllegalStateException if there is not exactly one email message
   */
  public synchronized MimeMessage onlyEmailMessage() {
    if (mailServer == null) {
      throw new IllegalStateException(
          "Email was disabled in the application config");
    }
    MimeMessage[] messages = mailServer.getReceivedMessages();
    try {
      mailServer.purgeEmailFromAllMailboxes();
    } catch (FolderException e) {
      throw new RuntimeException(e);
    }

    if (messages.length != 1)
      throw new IllegalStateException("There is not exactly one email");

    return messages[0];
  }

  /**
   * The {@linkplain Client HTTP client} that should be used for making requests
   * to the local {@linkplain HttpServer HTTP server} running the application.
   */
  public Client httpClient() {
    return httpClient;
  }

  /**
   * Produces a URI that points at the local HTTP server and that
   */
  public URI localUri(String path) {
    String protocol =
        (config.http.keyStore == null)
            ? "http"
            : "https";

    return UriBuilder.fromUri(protocol + "://localhost")
                     .port(config.http.port)
                     .path(path)
                     .build();
  }

  /**
   * A valid account id that exists.
   */
  public String accountId() {
    return "tester";
  }

  /**
   * Like {@link #accountId()}, but for another account.
   */
  public String otherAccountId() {
    return "other";
  }

  /**
   * An account id where no account with this id exists, but it's a
   * theoretically valid id.
   */
  public String unknownAccountId() {
    return "unknown";
  }

  /**
   * A value that can be submitted in the {@code Authorization} header of an
   * HTTP request to the local server, making that request pass authentication.
   */
  public String authorizationHeader() {
    return writeAuthorizationHeader("tester", "password");
  }

  /**
   * Like {@link #authorizationHeader()} but for a different account.
   */
  public String otherAuthorizationHeader() {
    return writeAuthorizationHeader("other", "password");
  }

  private static String writeAuthorizationHeader(String username,
                                                 String password) {
    Objects.requireNonNull(username);
    Objects.requireNonNull(password);
    return "Basic " +
        Base64.getEncoder().encodeToString(
            (username + ":" + password).getBytes(UTF_8));
  }

  private static ApplicationConfig newApplicationConfig() {
    URL url = Resources.getResource("test_config.yml");
    ByteSource bytes = Resources.asByteSource(url);
    return ConfigReader.readBytes(bytes);
  }

  private static Client newHttpClient(HttpServerConfig config) {
    ClientBuilder builder = ClientBuilder.newBuilder();

    builder.register(SseFeature.class);

    if (config.keyStore != null) {
      Path keyStoreFile = Paths.get(config.keyStore.path);

      builder.trustStore(
          KeyStores.readKeyStore(
              /* keyStoreBytes= */ MoreFiles.asByteSource(keyStoreFile),
              /* password= */ config.keyStore.password.toCharArray()));
    }

    return builder.build();
  }

  private static GreenMail newMailServer(EmailConfig config) {
    return new GreenMail(
        new ServerSetup(
            /* port= */ config.port,
            /* bindAddress= */ "localhost",
            /* protocol= */ "smtp"));
  }
}
