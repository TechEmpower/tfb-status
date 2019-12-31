package tfb.status.hk2.extensions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.inject.Scope;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.PerLookup;

/**
 * An annotation indicating that a method or field within a service is the
 * provider of another service.  The contract of the provided service is equal
 * to the return type of that method or the type of that field.
 *
 * <p>The scope of the provided service is:
 * <ul>
 * <li>{@link PerLookup} if the method or field is {@link Nullable}.
 * <li>Otherwise, the {@link Scope} annotation on the method or field, if
 *     present.
 * <li>Otherwise, the {@link Scope} annotation on the method return type or the
 *     field type, if present.
 * <li>Otherwise, the scope of the containing service.
 * </ul>
 *
 * <p>This annotation is only recognized on the methods and fields of
 * otherwise-valid service classes.  A service class that cannot be instantiated
 * through other means cannot use this annotation.  This implies that a service
 * class cannot declare a static provides method returning an instance of that
 * same class as the only means of instantiating that class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.FIELD })
public @interface Provides {}

//
// TODO: Re-examine how contracts of @Provides services are determined.
//
//       Are additional contracts picked up from the interfaces that the method
//       or field type implements?  If not, should they be?
//
//       Should there be a mechanism for declaring additional contracts, similar
//       to @ContractsProvided?
//
