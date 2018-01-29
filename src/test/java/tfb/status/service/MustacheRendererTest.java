package tfb.status.service;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static tfb.status.config.ResourceMode.CLASS_PATH;
import static tfb.status.config.ResourceMode.FILE_SYSTEM;
import static tfb.status.util.MoreStrings.linesOf;

import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tfb.status.config.MustacheConfig;

/**
 * Tests for {@link MustacheRenderer}.
 */
public final class MustacheRendererTest {
  /**
   * Tests that {@link MustacheRenderer#render(String, Object...)} is able to
   * render templates from the class path.
   */
  @Test
  public void testClassPathMode() {
    MustacheConfig config =
        new MustacheConfig(/* mode= */ CLASS_PATH,
                           /* root= */ "test_mustache");

    MustacheRenderer mustacheRenderer = new MustacheRenderer(config);

    HelloView view = new HelloView("hello");
    String html = mustacheRenderer.render("hello.mustache", view);

    assertIterableEquals(
        List.of("<b>hello</b>"),
        linesOf(html));
  }

  /**
   * Tests that {@link MustacheRenderer#render(String, Object...)} is able to
   * render templates from the file system.
   */
  @Test
  public void testFileSystemMode() {
    MustacheConfig config =
        new MustacheConfig(/* mode= */ FILE_SYSTEM,
                           /* root= */ "src/test/resources/test_mustache");

    MustacheRenderer mustacheRenderer = new MustacheRenderer(config);

    HelloView view = new HelloView("hello");
    String html = mustacheRenderer.render("hello.mustache", view);

    assertIterableEquals(
        List.of("<b>hello</b>"),
        linesOf(html));
  }

  public static final class HelloView {
    public final String message;

    public HelloView(String message) {
      this.message = Objects.requireNonNull(message);
    }
  }
}
