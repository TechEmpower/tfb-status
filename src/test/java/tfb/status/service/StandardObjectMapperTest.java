package tfb.status.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableListMultimap;
import java.io.IOException;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.testlib.TestServices;

/**
 * Tests for {@link StandardObjectMapper}.
 */
public final class StandardObjectMapperTest {
  private static TestServices services;
  private static ObjectMapper objectMapper;

  @BeforeAll
  public static void beforeAll() {
    services = new TestServices();
    objectMapper = services.serviceLocator().getService(ObjectMapper.class);
  }

  @AfterAll
  public static void afterAll() {
    services.shutdown();
  }

  /**
   * Verifies that the object mapper is compatible with certain important types
   * from the Guava library, especially during deserialization.
   */
  @Test
  public void testGuavaTypesEnabled() throws IOException {
    ImmutableListMultimap<String, Integer> multimap =
        ImmutableListMultimap.of(
            "odd", 1,
            "odd", 3,
            "even", 2);

    GuavaObject object = new GuavaObject(multimap);
    String json = objectMapper.writeValueAsString(object);
    assertEquals("{\"multimap\":{\"odd\":[1,3],\"even\":[2]}}", json);

    GuavaObject deserialized = objectMapper.readValue(json, GuavaObject.class);
    assertEquals(multimap, deserialized.multimap);
  }

  public static final class GuavaObject {
    public final ImmutableListMultimap<String, Integer> multimap;

    @JsonCreator
    public GuavaObject(

        @JsonProperty(value = "multimap", required = true)
        ImmutableListMultimap<String, Integer> multimap) {

      this.multimap = Objects.requireNonNull(multimap);
    }
  }

  /**
   * Verifies that the object mapper does not throw an exception when
   * deserializing JSON that contains unrecognized properties
   */
  @Test
  public void testUnknownPropertiesIgnored() throws IOException {
    String json = "{\"value\":50,\"somethingElse\":2}";
    IntBox box = objectMapper.readValue(json, IntBox.class);
    assertEquals(50, box.value);
  }

  public static final class IntBox {
    public final int value;

    @JsonCreator
    public IntBox(@JsonProperty("value") int value) {
      this.value = value;
    }
  }
}
