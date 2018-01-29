package tfb.status.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Objects;
import org.glassfish.hk2.api.Factory;

/**
 * Provides the standard {@link ObjectMapper} used by the application.
 */
public final class StandardObjectMapper implements Factory<ObjectMapper> {
  @Override
  public ObjectMapper provide() {
    return new ObjectMapper()
        .registerModule(new GuavaModule())
        .registerModule(new Jdk8Module())
        .registerModule(new JavaTimeModule())
        .configure(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false);
  }

  @Override
  public void dispose(ObjectMapper instance) {
    Objects.requireNonNull(instance);
    // No cleanup required.
  }
}
