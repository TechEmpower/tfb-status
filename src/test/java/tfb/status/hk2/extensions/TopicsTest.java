package tfb.status.hk2.extensions;

import static org.glassfish.hk2.utilities.ServiceLocatorUtilities.createAndPopulateServiceLocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tfb.status.hk2.extensions.CompatibleWithJava8.listOf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.messaging.MessageReceiver;
import org.glassfish.hk2.api.messaging.SubscribeTo;
import org.glassfish.hk2.api.messaging.Topic;
import org.glassfish.hk2.api.messaging.TopicDistributionService;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TopicsModule} and supporting classes.
 */
public final class TopicsTest {
  /**
   * Verifies that {@link Topic#publish(Object)} distributes the message to all
   * subscribers.  Verifies the existence of a {@link TopicDistributionService}.
   */
  @Test
  public void testTopics() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.bind(locator, new TopicsModule());
    ServiceLocatorUtilities.addClasses(
        locator,
        Topics.class,
        ServiceWithLifecycle.class,
        SingletonServiceWithShutdown.class,
        SubscriberService.class);

    Topics topics = locator.getService(Topics.class);

    topics.stringTopic.publish("1");
    topics.integerTopic.publish(2);
    topics.charSequenceTopic.publish("3");

    SubscriberService service = locator.getService(SubscriberService.class);

    assertEquals(
        listOf("1", 2),
        service.getMessages());

    List<ServiceWithLifecycle> service1List = service.getService1List();
    List<SingletonServiceWithShutdown> service2List = service.getService2List();

    List<Boolean> serviced1WasShutdown = service.getService1WasShutdown();
    List<Boolean> serviced2WasShutdown = service.getService2WasShutdown();

    assertEquals(2, service1List.size());
    assertEquals(2, service2List.size());

    assertEquals(2, serviced1WasShutdown.size());
    assertEquals(2, serviced2WasShutdown.size());

    assertFalse(serviced1WasShutdown.get(0));
    assertFalse(serviced1WasShutdown.get(1));
    assertTrue(service1List.get(0).wasStopped());
    assertTrue(service1List.get(1).wasStopped());
    assertNotSame(service1List.get(0), service1List.get(1));

    assertFalse(serviced2WasShutdown.get(0));
    assertFalse(serviced2WasShutdown.get(1));
    assertFalse(service2List.get(0).wasStopped());
    assertFalse(service2List.get(1).wasStopped());
    assertSame(service2List.get(0), service2List.get(1));
  }

  public static final class Topics {
    final Topic<String> stringTopic;
    final Topic<Integer> integerTopic; // subtype of Number, should be seen
    final Topic<CharSequence> charSequenceTopic; // should be ignored

    @Inject
    public Topics(Topic<String> stringTopic,
                  Topic<Integer> integerTopic,
                  Topic<CharSequence> charSequenceTopic) {

      this.stringTopic = Objects.requireNonNull(stringTopic);
      this.integerTopic = Objects.requireNonNull(integerTopic);
      this.charSequenceTopic = Objects.requireNonNull(charSequenceTopic);
    }
  }

  public static class ServiceWithLifecycle implements PostConstruct, PreDestroy {
    /*@GuardedBy("this")*/
    private boolean wasStarted = false;

    /*@GuardedBy("this")*/
    private boolean wasStopped = false;

    public synchronized boolean wasStarted() {
      return wasStarted;
    }

    public synchronized boolean wasStopped() {
      return wasStopped;
    }

    public synchronized void start() {
      wasStarted = true;
    }

    public synchronized void stop() {
      wasStopped = true;
    }

    @Override
    public void postConstruct() {
      start();
    }

    @Override
    public void preDestroy() {
      stop();
    }
  }

  @Singleton
  public static class SingletonServiceWithShutdown
      extends ServiceWithLifecycle {}

  @Singleton
  @MessageReceiver({ String.class, Number.class })
  public static final class SubscriberService {
    /*@GuardedBy("this")*/
    private final List<Object> messages = new ArrayList<>();

    /*@GuardedBy("this")*/
    private final List<ServiceWithLifecycle> service1List = new ArrayList<>();

    /*@GuardedBy("this")*/
    private final List<SingletonServiceWithShutdown> service2List = new ArrayList<>();

    /*@GuardedBy("this")*/
    private final List<Boolean> service1WasShutdown = new ArrayList<>();

    /*@GuardedBy("this")*/
    private final List<Boolean> service2WasShutdown = new ArrayList<>();

    public synchronized void onEvent(@SubscribeTo Object message,
                                     ServiceWithLifecycle service1,
                                     SingletonServiceWithShutdown service2) {
      messages.add(message);
      service1List.add(service1);
      service2List.add(service2);
      service1WasShutdown.add(service1.wasStopped());
      service2WasShutdown.add(service2.wasStopped());
    }

    public synchronized List<Object> getMessages() {
      return new ArrayList<>(messages);
    }

    public synchronized List<ServiceWithLifecycle> getService1List() {
      return new ArrayList<>(service1List);
    }

    public synchronized List<SingletonServiceWithShutdown> getService2List() {
      return new ArrayList<>(service2List);
    }

    public synchronized List<Boolean> getService1WasShutdown() {
      return new ArrayList<>(service1WasShutdown);
    }

    public synchronized List<Boolean> getService2WasShutdown() {
      return new ArrayList<>(service2WasShutdown);
    }
  }
}
