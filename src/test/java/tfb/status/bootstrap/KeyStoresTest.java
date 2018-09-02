package tfb.status.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link KeyStores}.
 */
public final class KeyStoresTest {
  /**
   * Verifies that {@link KeyStores#readKeyStore(ByteSource, char[])} is able to
   * open a valid key store file.
   */
  @Test
  public void testReadKeyStore() throws GeneralSecurityException {
    URL url = Resources.getResource("localhost.jks");
    ByteSource bytes = Resources.asByteSource(url);
    char[] password = "password".toCharArray();
    KeyStore keyStore = KeyStores.readKeyStore(bytes, password);
    assertNotNull(keyStore.getKey("localhost", password));
  }

  /**
   * Verifies that {@link KeyStores#readServerSslContext(ByteSource, char[])} is
   * able to open a valid key store file.
   */
  @Test
  public void testReadServerSslContext() {
    URL url = Resources.getResource("localhost.jks");
    ByteSource bytes = Resources.asByteSource(url);
    char[] password = "password".toCharArray();
    SSLContext sslContext = KeyStores.readServerSslContext(bytes, password);
    assertEquals("TLS", sslContext.getProtocol());

  }

  /**
   * Verifies that {@link KeyStores#readClientSslContext(ByteSource, char[])} is
   * able to open a valid key store file.
   */
  @Test
  public void testReadClientSslContext() {
    URL url = Resources.getResource("localhost.jks");
    ByteSource bytes = Resources.asByteSource(url);
    char[] password = "password".toCharArray();
    SSLContext sslContext = KeyStores.readClientSslContext(bytes, password);
    assertEquals("TLS", sslContext.getProtocol());
  }
}
