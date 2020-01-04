package tfb.status.hk2.extensions;

import org.glassfish.hk2.utilities.binding.AbstractBinder;

/**
 * Enables the {@link Provides} annotation.
 */
public final class ProvidesModule extends AbstractBinder {
  @Override
  protected void configure() {
    addActiveDescriptor(ProvidesAnnotationEnabler.class);
  }
}
