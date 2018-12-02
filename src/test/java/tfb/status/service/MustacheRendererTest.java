package tfb.status.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static tfb.status.testlib.MoreAssertions.assertHtmlDocument;

import com.github.mustachejava.MustacheNotFoundException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.testlib.TestServices;

/**
 * Tests for {@link MustacheRenderer}.
 */
public final class MustacheRendererTest {
  private static TestServices services;
  private static MustacheRenderer mustacheRenderer;

  @BeforeAll
  public static void beforeAll() {
    services = new TestServices();
    mustacheRenderer = services.serviceLocator().getService(MustacheRenderer.class);
  }

  @AfterAll
  public static void afterAll() {
    services.shutdown();
  }

  /**
   * Verifies that {@link MustacheRenderer#render(String, Object...)} is able to
   * render a known mustache template.
   */
  @Test
  public void testRenderKnownTemplate() {
    String html = mustacheRenderer.render("home.mustache");
    assertHtmlDocument(html);
  }

  /**
   * Verifies that {@link MustacheRenderer#render(String, Object...)} throws
   * {@link MustacheNotFoundException} when given a template file name that does
   * not exist.
   */
  @Test
  public void testRenderUnknownTemplate() {
    assertThrows(
        MustacheNotFoundException.class,
        () -> mustacheRenderer.render("not_a_real_template.mustache"));
  }
}
