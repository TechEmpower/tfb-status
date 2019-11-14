/**
 * Internal services available to other components of this application.
 */
@CheckReturnValue
@DefaultQualifier(
    value = NonNull.class,
    locations = {
        TypeUseLocation.FIELD,
        TypeUseLocation.PARAMETER,
        TypeUseLocation.RETURN
    })
package tfb.status.service;

import com.google.errorprone.annotations.CheckReturnValue;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.TypeUseLocation;
