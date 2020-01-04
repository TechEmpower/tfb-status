package tfb.status.hk2.extensions;

import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.messaging.Topic;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;

/**
 * Utility methods for working with {@link ServiceLocator}.
 */
public final class Services {
  private Services() {
    throw new AssertionError("This class cannot be instantiated");
  }

  /**
   * Returns a new {@link ServiceLocator} instance with {@link Topic}, {@link
   * Provides}, and {@link Registers} enabled.
   */
  public static ServiceLocator newServiceLocator() {
    ServiceLocator serviceLocator =
        ServiceLocatorUtilities.createAndPopulateServiceLocator();

    // TODO: Consider making these classes public then removing this method.
    ServiceLocatorUtilities.addClasses(
        serviceLocator,
        TopicDistributionServiceImpl.class,
        ProvidesAnnotationEnabler.class);

    return serviceLocator;
  }

}
