package tfb.status.bootstrap;

import java.util.Objects;

/**
 * An exception thrown when a Java KeyStore file cannot be loaded.  See the
 * {@linkplain #getCause() cause} of this exception for details.
 *
 * <p>Possible reasons for this exception to be thrown include (but are not
 * limited to):
 *
 * <ul>
 * <li>The KeyStore file does not exist.
 * <li>The KeyStore file does exist, but it cannot be read by the current
 *     process due to file permissions or due to other I/O-level problems.
 * <li>The KeyStore file is not in a supported format.
 * <li>The KeyStore file is password-protected and the provided password is
 *     incorrect.
 * </ul>
 */
public final class InvalidKeyStoreException extends RuntimeException {
  /**
   * Constructs a new invalid key store exception with the provided cause.
   *
   * @param cause the cause of this exception
   */
  public InvalidKeyStoreException(Throwable cause) {
    super(Objects.requireNonNull(cause));
  }

  private static final long serialVersionUID = 0;
}
