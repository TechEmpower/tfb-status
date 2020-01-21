package tfb.status.hk2.extensions;

import org.glassfish.hk2.api.messaging.Topic;
import org.glassfish.hk2.api.messaging.TopicDistributionService;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

/**
 * Enables {@link Topic} distribution.
 *
 * <p>The {@link TopicDistributionService} implementation is similar to the
 * default topic distribution service provided in {@code hk2-extras}, except
 * this implementation will construct services in order to deliver messages to
 * them.
 */
public final class TopicsModule extends AbstractBinder {
  @Override
  protected void configure() {
    addActiveDescriptor(TopicDistributionServiceImpl.class);
  }
}
