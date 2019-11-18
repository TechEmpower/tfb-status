package tfb.status.testlib;

import static org.glassfish.hk2.api.InstanceLifecycleEventType.PRE_PRODUCTION;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.InstanceLifecycleEvent;
import org.glassfish.hk2.api.InstanceLifecycleListener;
import tfb.status.service.EmailSender;

/**
 * Ensures that the {@link MailServer} is running before the {@link EmailSender}
 * is used.
 */
@Singleton
final class MailServerDependency implements InstanceLifecycleListener {
  private final Provider<MailServer> mailServerProvider;

  @Inject
  public MailServerDependency(Provider<MailServer> mailServerProvider) {
    this.mailServerProvider = Objects.requireNonNull(mailServerProvider);
  }

  @Override
  public Filter getFilter() {
    return descriptor -> descriptor.getAdvertisedContracts()
                                   .contains(EmailSender.class.getTypeName());
  }

  @Override
  public void lifecycleEvent(InstanceLifecycleEvent lifecycleEvent) {
    if (lifecycleEvent.getEventType() == PRE_PRODUCTION) {
      MailServer server = mailServerProvider.get();
      server.start();
    }
  }
}
