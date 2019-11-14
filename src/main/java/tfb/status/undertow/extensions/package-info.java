/**
 * Classes that may be useful in any Undertow application, not just this one.
 */
@CheckReturnValue
@DefaultQualifier(
    value = NonNull.class,
    locations = {
        TypeUseLocation.FIELD,
        TypeUseLocation.PARAMETER,
        TypeUseLocation.RETURN
    })
package tfb.status.undertow.extensions;

import com.google.errorprone.annotations.CheckReturnValue;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.TypeUseLocation;
