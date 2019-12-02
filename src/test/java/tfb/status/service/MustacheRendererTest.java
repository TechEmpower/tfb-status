package tfb.status.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static tfb.status.testlib.MoreAssertions.assertHtmlDocument;

import com.github.mustachejava.MustacheNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link MustacheRenderer}.
 */
@ExtendWith(TestServicesInjector.class)
public final class MustacheRendererTest {
  /**
   * Verifies that {@link MustacheRenderer#render(String, Object...)} is able to
   * render a known mustache template.
   */
  @Test
  public void testRenderKnownTemplate(MustacheRenderer mustacheRenderer) {
    String html = mustacheRenderer.render("home.mustache");
    assertHtmlDocument(html);
  }

  /**
   * Verifies that {@link MustacheRenderer#render(String, Object...)} throws
   * {@link MustacheNotFoundException} when given a template file name that does
   * not exist.
   */
  @Test
  public void testRenderUnknownTemplate(MustacheRenderer mustacheRenderer) {
    assertThrows(
        MustacheNotFoundException.class,
        () -> mustacheRenderer.render("not_a_real_template.mustache"));
  }
}
