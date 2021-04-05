package tfb.status.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import jakarta.inject.Singleton;
import tfb.status.hk2.extensions.Provides;

/**
 * Provides the {@link ObjectMapper} used by this application.
 */
public final class ObjectMapperFactory {
  private ObjectMapperFactory() {
    throw new AssertionError("This class cannot be instantiated");
  }

  @Provides
  @Singleton
  public static ObjectMapper objectMapper() {
    return new ObjectMapper()
        .registerModule(new GuavaModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }
}
