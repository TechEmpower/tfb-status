package tfb.status.bootstrap;

import java.util.Objects;
import tfb.status.config.ApplicationConfig;

/**
 * An exception thrown when this application's configuration file cannot be
 * deserialized into an {@link ApplicationConfig} object for any reason.
 *
 * <p>Possible reasons for this exception to be thrown include, but are not
 * limited to:
 *
 * <ul>
 * <li>The configuration file does not exist.
 * <li>The configuration file is not in a supported format.
 * <li>The configuration file is missing required properties.
 * </ul>
 */
public final class InvalidConfigFileException extends RuntimeException {
  /**
   * Constructs a new invalid config file exception with the provided message.
   *
   * @param message the error message
   */
  public InvalidConfigFileException(String message) {
    super(Objects.requireNonNull(message));
  }

  /**
   * Constructs a new invalid config file exception with the provided message
   * and cause.
   *
   * @param message the error message
   * @param cause the cause of this exception
   */
  public InvalidConfigFileException(String message, Throwable cause) {
    super(Objects.requireNonNull(message),
          Objects.requireNonNull(cause));
  }

  private static final long serialVersionUID = 0;
}
