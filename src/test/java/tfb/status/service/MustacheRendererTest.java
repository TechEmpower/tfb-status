package tfb.status.service;

import static tfb.status.config.ResourceMode.CLASS_PATH;
import static tfb.status.config.ResourceMode.FILE_SYSTEM;
import static tfb.status.util.MoreAssertions.assertLinesEqual;

import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tfb.status.config.MustacheConfig;

/**
 * Tests for {@link MustacheRenderer}.
 */
public final class MustacheRendererTest {
  /**
   * Verifies that {@link MustacheRenderer#render(String, Object...)} is able to
   * render templates from the class path.
   */
  @Test
  public void testClassPathMode() {
    var config =
        new MustacheConfig(
            /* mode= */ CLASS_PATH,
            /* root= */ "test_mustache");

    var mustacheRenderer = new MustacheRenderer(config);

    var view = new HelloView("hello");

    String html = mustacheRenderer.render("hello.mustache", view);

    assertLinesEqual(
        List.of("<b>hello</b>"),
        html);
  }

  /**
   * Verifies that {@link MustacheRenderer#render(String, Object...)} is able to
   * render templates from the file system.
   */
  @Test
  public void testFileSystemMode() {
    var config =
        new MustacheConfig(
            /* mode= */ FILE_SYSTEM,
            /* root= */ "src/test/resources/test_mustache");

    var mustacheRenderer = new MustacheRenderer(config);

    var view = new HelloView("hello");

    String html = mustacheRenderer.render("hello.mustache", view);

    assertLinesEqual(
        List.of("<b>hello</b>"),
        html);
  }

  public static final class HelloView {
    public final String message;

    public HelloView(String message) {
      this.message = Objects.requireNonNull(message);
    }
  }
}
