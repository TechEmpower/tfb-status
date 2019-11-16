package tfb.status.bootstrap;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Factory;

/**
 * Provides the {@link ObjectMapper} used by this application.
 */
@Singleton
final class ObjectMapperFactory implements Factory<ObjectMapper> {
  @Override
  @Singleton
  public ObjectMapper provide() {
    return new ObjectMapper()
        .registerModule(new GuavaModule())
        .configure(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false);
  }

  @Override
  public void dispose(ObjectMapper instance) {
    // No cleanup required.
  }
}