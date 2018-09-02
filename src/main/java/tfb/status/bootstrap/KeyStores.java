package tfb.status.bootstrap;

import com.google.common.io.ByteSource;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Objects;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Utility methods for working with {@link KeyStore} and {@link SSLContext}.
 */
public final class KeyStores {
  private KeyStores() {
    throw new AssertionError("This class cannot be instantiated");
  }

  /**
   * Produces a {@link KeyStore} instance from a Java KeyStore (JKS) file.
   *
   * @param keyStoreBytes the bytes of the key store
   * @param password the password for the key store
   * @return a key store
   * @throws InvalidKeyStoreException if the key store cannot be loaded
   */
  public static KeyStore readKeyStore(ByteSource keyStoreBytes,
                                      char[] password) {

    Objects.requireNonNull(keyStoreBytes);
    Objects.requireNonNull(password);

    KeyStore keyStore = newDefaultKeyStore();
    try (InputStream inputStream = keyStoreBytes.openStream()) {
      keyStore.load(inputStream, password);
    } catch (IOException | GeneralSecurityException e) {
      throw new InvalidKeyStoreException(e);
    }

    return keyStore;
  }

  /**
   * Produces an {@link SSLContext} instance from a Java KeyStore (JKS) file for
   * server-side use; the key store contains the certificate(s) to be presented
   * by the server.
   *
   * @param keyStoreBytes the bytes of the key store
   * @param password the password for the key store
   * @return an SSL context
   * @throws InvalidKeyStoreException if the key store cannot be loaded
   */
  public static SSLContext readServerSslContext(ByteSource keyStoreBytes,
                                                char[] password) {

    KeyStore keyStore = readKeyStore(keyStoreBytes, password);

    KeyManagerFactory keyManagerFactory = newDefaultKeyManagerFactory();
    try {
      keyManagerFactory.init(keyStore, password);
    } catch (GeneralSecurityException e) {
      throw new InvalidKeyStoreException(e);
    }

    SSLContext sslContext = newTlsSslContext();
    try {
      sslContext.init(
          /* km= */ keyManagerFactory.getKeyManagers(),
          /* tm= */ null,
          /* random= */ null);
    } catch (GeneralSecurityException e) {
      throw new InvalidKeyStoreException(e);
    }

    return sslContext;
  }

  /**
   * Produces an {@link SSLContext} instance from a Java KeyStore (JKS) file for
   * client-side use; the key store contains the certificate(s) to be trusted by
   * the client.
   *
   * @param keyStoreBytes the bytes of the key store
   * @param password the password for the key store
   * @return an SSL context
   * @throws InvalidKeyStoreException if the key store cannot be loaded
   */
  public static SSLContext readClientSslContext(ByteSource keyStoreBytes,
                                                char[] password) {

    KeyStore keyStore = readKeyStore(keyStoreBytes, password);

    TrustManagerFactory trustManagerFactory = newDefaultTrustManagerFactory();
    try {
      trustManagerFactory.init(keyStore);
    } catch (GeneralSecurityException e) {
      throw new InvalidKeyStoreException(e);
    }

    SSLContext sslContext = newTlsSslContext();
    try {
      sslContext.init(
          /* km= */ null,
          /* tm= */ trustManagerFactory.getTrustManagers(),
          /* random= */ null);
    } catch (GeneralSecurityException e) {
      throw new InvalidKeyStoreException(e);
    }

    return sslContext;
  }

  /**
   * Returns a new, not-yet-loaded {@link KeyStore} of the default type.
   */
  private static KeyStore newDefaultKeyStore() {
    String type = KeyStore.getDefaultType();
    try {
      return KeyStore.getInstance(type);
    } catch (GeneralSecurityException impossible) {
      throw new AssertionError(
          "The default KeyStore type is always supported",
          impossible);
    }
  }

  /**
   * Returns a new, not-yet-initialized {@link KeyManagerFactory} that uses the
   * default algorithm.
   */
  private static KeyManagerFactory newDefaultKeyManagerFactory() {
    String algorithm = KeyManagerFactory.getDefaultAlgorithm();
    try {
      return KeyManagerFactory.getInstance(algorithm);
    } catch (GeneralSecurityException impossible) {
      throw new AssertionError(
          "The default KeyManagerFactory algorithm is always supported",
          impossible);
    }
  }

  /**
   * Returns a new, not-yet-initialized {@link TrustManagerFactory} that uses
   * the default algorithm.
   */
  private static TrustManagerFactory newDefaultTrustManagerFactory() {
    String algorithm = TrustManagerFactory.getDefaultAlgorithm();
    try {
      return TrustManagerFactory.getInstance(algorithm);
    } catch (GeneralSecurityException impossible) {
      throw new AssertionError(
          "The default TrustManagerFactory algorithm is always supported",
          impossible);
    }
  }

  /**
   * Returns a new, not-yet-initialized {@link SSLContext} that uses the TLS
   * protocol.
   */
  private static SSLContext newTlsSslContext() {
    try {
      return SSLContext.getInstance(/* protocol= */ "TLS");
    } catch (GeneralSecurityException impossible) {
      throw new AssertionError("TLS is always supported", impossible);
    }
  }
}
