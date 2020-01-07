package tfb.status.hk2.extensions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.inject.Scope;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.hk2.api.PreDestroy;
import org.jvnet.hk2.annotations.ContractsProvided;

/**
 * An annotation indicating that a method or field is the provider of a service.
 *
 * Example usage:
 *
 * <pre>
 *   public class MyService {
 *     &#64;Inject
 *     public MyService( ... ) { ... }
 *
 *     &#64;Provides
 *     public OtherService other() {
 *       return new OtherService( ... );
 *     }
 *   }
 *
 *   ServiceLocator locator =
 *       ServiceLocatorUtilities.createAndPopulateServiceLocator();
 *
 *   ServiceLocatorUtilities.bind(locator, new ProvidesModule());
 *   ServiceLocatorUtilities.addClasses(locator, MyService.class);
 *   OtherService other = locator.getService(OtherService.class);
 * </pre>
 *
 * <h2>Contracts</h2>
 *
 * <p>The contracts of the provided service are, by default, defined by the
 * {@linkplain Method#getGenericReturnType() method return type} of the
 * annotated method or the {@linkplain Field#getGenericType() field type} of the
 * annotated field.  These default contracts can be overridden using {@link
 * #contracts()}.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>The providing class is responsible for initializing instances of the
 * provided service.  The system will not automatically invoke a {@link
 * PostConstruct#postConstruct()} method declared by the provided service.
 *
 * <p>If the annotated member is not a static field, then disposal of the
 * provided service may be customized.  See {@link #disposeMethod()}.  If the
 * annotated member is a static field, then the provided service will not
 * undergo automatic disposal.
 *
 * <h2>Scope</h2>
 *
 * <p>The scope of the provided service is:
 * <ul>
 * <li>{@link PerLookup} if the method or field is {@link Nullable}.
 * <li>Otherwise, the {@link Scope} annotation on the method or field, if
 *     present.
 * <li>Otherwise, the {@link Scope} annotation on the method return type or the
 *     field type, if present.
 * <li>Otherwise, if the method or field is non-static, the scope of the
 *     containing service.
 * <li>Otherwise, {@link PerLookup}.
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.FIELD })
public @interface Provides {
  /**
   * If non-empty, specifies a list of contracts provided that overrides the
   * default contracts.  Similar to {@link ContractsProvided}.
   *
   * <p>If empty, then the default contracts for the provided type will be used.
   */
  Class<?>[] contracts() default {};

  /**
   * If non-empty, specifies the name of the method to be invoked when disposing
   * of an instance of the provided service.  The class whose method is to be
   * invoked is specified by {@link #disposalHandledBy()}.
   *
   * <p>If empty, then the default pre-destroy behavior for the provided type
   * will be used.  If the provided type implements {@link PreDestroy} for
   * example, then its {@link PreDestroy#preDestroy()} method will be invoked.
   *
   * <p>This value is ignored when this annotation is applied to a static field.
   */
  String disposeMethod() default "";

  /**
   * Specifies who is responsible for the disposal of instances of the provided
   * service, assuming that {@link #disposeMethod()} is non-empty.  If {@link
   * #disposeMethod()} is empty then this value is ignored.
   *
   * <p>See {@link DisposalHandledBy} for definitions of the possible values.
   */
  DisposalHandledBy disposalHandledBy()
      default DisposalHandledBy.PROVIDED_INSTANCE;

  /**
   * Specifies who is responsible for the disposal of instances of a service.
   */
  enum DisposalHandledBy {
    /**
     * The instance of the service that is provided is responsible for its own
     * disposal.  {@link #disposeMethod()} names a non-static, zero-parameter,
     * public method of the provided service type.
     */
    PROVIDED_INSTANCE,

    /**
     * The instance or class that provides the service &mdash; the one declaring
     * a method or field annotated with {@link Provides} &mdash; is responsible
     * for the disposal of instances of the provided service.  {@link
     * #disposeMethod()} names a public method of the providing class that
     * accepts one parameter, where the type of that parameter is a supertype of
     * the provided service type.  If the method or field annotated with {@link
     * Provides} is static, then the dispose method must also be static.
     * Otherwise the dispose method must be non-static.
     */
    PROVIDER
  }
}
