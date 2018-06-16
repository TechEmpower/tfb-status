package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * The configuration for emails sent by this application.
 */
@Immutable
public final class EmailConfig {
  /**
   * The hostname of the mail server.
   */
  public final String host;

  /**
   * The port for the mail server.
   */
  public final int port;

  /**
   * The email address for the "from" field.
   */
  public final String from;

  /**
   * The email address for the "to" field.
   */
  public final String to;

  @JsonCreator
  public EmailConfig(

      @JsonProperty(value = "host", required = true)
      String host,

      @JsonProperty(value = "port", required = true)
      int port,

      @JsonProperty(value = "from", required = true)
      String from,

      @JsonProperty(value = "to", required = true)
      String to) {

    this.host = Objects.requireNonNull(host);
    this.port = port;
    this.from = Objects.requireNonNull(from);
    this.to = Objects.requireNonNull(to);
  }
}
