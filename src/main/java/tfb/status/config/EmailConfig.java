package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import jakarta.inject.Singleton;
import java.util.Objects;

/**
 * The configuration for emails sent by this application.
 *
 * @param host The hostname of the mail server.
 * @param port The port for the mail server.  If this value is zero, then the
 *        mail server listens on an ephemeral port.  The ephemeral port is
 *        dynamically assigned to the mail server by the host system as the mail
 *        server is started.  Mail clients are responsible for determining the
 *        ephemeral port number of the server somehow.  The algorithm for doing
 *        so is not specified by this class.
 * @param username The username for authenticating with the mail server.
 * @param password The password for authenticating with the mail server.
 * @param from The email address for the "from" field.
 * @param to The email address for the "to" field.
 */
@Immutable
@Singleton
public record EmailConfig(String host,
                          int port,
                          String username,
                          String password,
                          String from,
                          String to) {

  public EmailConfig {
    Objects.requireNonNull(host);
    Objects.requireNonNull(username);
    Objects.requireNonNull(password);
    Objects.requireNonNull(from);
    Objects.requireNonNull(to);
  }

  @JsonCreator
  public static EmailConfig create(
      @JsonProperty(value = "host", required = true)
      String host,

      @JsonProperty(value = "port", required = true)
      int port,

      @JsonProperty(value = "username", required = true)
      String username,

      @JsonProperty(value = "password", required = true)
      String password,

      @JsonProperty(value = "from", required = true)
      String from,

      @JsonProperty(value = "to", required = true)
      String to) {

    return new EmailConfig(host, port, username, password, from, to);
  }
}
