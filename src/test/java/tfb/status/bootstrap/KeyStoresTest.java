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
   * Tests that {@link KeyStores#configuredSslContext(ByteSource, char[])} is
   * able to open a valid key store file.
   */
  @Test
  public void testConfiguredSslContext() {
    URL url = Resources.getResource("localhost.jks");
    ByteSource bytes = Resources.asByteSource(url);
    char[] password = "password".toCharArray();
    SSLContext sslContext = KeyStores.configuredSslContext(bytes, password);
    assertEquals("TLS", sslContext.getProtocol());
  }

  /**
   * Tests that {@link KeyStores#configuredKeyStore(ByteSource, char[])} is able
   * to open a valid key store file.
   */
  @Test
  public void testConfiguredKeyStore() throws GeneralSecurityException {
    URL url = Resources.getResource("localhost.jks");
    ByteSource bytes = Resources.asByteSource(url);
    char[] password = "password".toCharArray();
    KeyStore keyStore = KeyStores.configuredKeyStore(bytes, password);
    assertNotNull(keyStore.getKey("localhost", password));
  }
}
