package tfb.status.undertow.extensions;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.net.MediaType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MediaTypes}.
 */
public final class MediaTypesTest {
  /**
   * Verifies that {@link MediaTypes#SPECIFICITY_ORDER} orders {@link MediaType}
   * instances as advertised.
   */
  @Test
  public void testSpecificityOrder() {
    MediaType any = MediaType.parse("*/*");
    MediaType text = MediaType.parse("text/*");
    MediaType plainText = MediaType.parse("text/plain");
    MediaType plainTextUtf8 = MediaType.parse("text/plain;charset=utf-8");
    MediaType plainTextUtf8Flowed =
        MediaType.parse("text/plain;charset=utf-8;format=flowed");

    assertEquals(
        List.of(any, text, plainText, plainTextUtf8, plainTextUtf8Flowed),
        List.of(text, plainTextUtf8Flowed, any, plainTextUtf8, plainText)
            .stream()
            .sorted(MediaTypes.SPECIFICITY_ORDER)
            .collect(toList()));
  }
}
