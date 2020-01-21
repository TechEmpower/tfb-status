package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The configuration for emails sent by this application.
 */
@Immutable
@Singleton
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

  public EmailConfig(String host, int port, String from, String to) {
    this.host = Objects.requireNonNull(host);
    this.port = port;
    this.from = Objects.requireNonNull(from);
    this.to = Objects.requireNonNull(to);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    } else if (!(object instanceof EmailConfig)) {
      return false;
    } else {
      EmailConfig that = (EmailConfig) object;
      return this.port == that.port
          && this.host.equals(that.host)
          && this.from.equals(that.from)
          && this.to.equals(that.to);
    }
  }

  @Override
  public int hashCode() {
    int hash = 1;
    hash = 31 * hash + host.hashCode();
    hash = 31 * hash + Integer.hashCode(port);
    hash = 31 * hash + from.hashCode();
    hash = 31 * hash + to.hashCode();
    return hash;
  }

  @JsonCreator
  public static EmailConfig create(
      @JsonProperty(value = "host", required = true)
      String host,

      @JsonProperty(value = "port", required = true)
      int port,

      @JsonProperty(value = "from", required = true)
      String from,

      @JsonProperty(value = "to", required = true)
      String to) {

    return new EmailConfig(host, port, from, to);
  }
}
