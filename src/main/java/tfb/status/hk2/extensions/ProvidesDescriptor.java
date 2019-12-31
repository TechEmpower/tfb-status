package tfb.status.hk2.extensions;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Named;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.DescriptorType;
import org.glassfish.hk2.api.DescriptorVisibility;
import org.glassfish.hk2.api.HK2Loader;
import org.glassfish.hk2.api.Injectee;
import org.glassfish.hk2.api.ProxyForSameScope;
import org.glassfish.hk2.api.Rank;
import org.glassfish.hk2.api.UseProxy;
import org.jvnet.hk2.internal.Collector;
import org.jvnet.hk2.internal.Utilities;

/**
 * An {@link ActiveDescriptor} that describes a method or field annotated with
 * {@link Provides}.
 */
abstract class ProvidesDescriptor implements ActiveDescriptor<Object> {
  /**
   * The method or field that is annotated with {@link Provides}.
   */
  abstract AnnotatedElement annotatedElement();

  @Override
  public final boolean isReified() {
    return true;
  }

  @Override
  public final Class<? extends Annotation> getScopeAnnotation() {
    return getScopeAsAnnotation().annotationType();
  }

  @Override
  public final Set<Annotation> getQualifierAnnotations() {
    Collector collector = new Collector();
    Set<Annotation> qualifiers =
        Utilities.getAllQualifiers(
            annotatedElement(),
            getName(),
            collector);
    collector.throwIfErrors();
    return ImmutableSet.copyOf(qualifiers);
  }

  @Override
  public final List<Injectee> getInjectees() {
    return ImmutableList.of();
  }

  @Override
  public final @Nullable Long getFactoryServiceId() {
    return null;
  }

  @Override
  public final @Nullable Long getFactoryLocatorId() {
    return null;
  }

  @Override
  public final @Nullable String getImplementation() {
    return getImplementationType().getTypeName();
  }

  @Override
  public final Set<String> getAdvertisedContracts() {
    return getContractTypes()
        .stream()
        .map(contract -> TypeToken.of(contract))
        .map(contract -> contract.getRawType())
        .map(contract -> contract.getName())
        .collect(toImmutableSet());
  }

  @Override
  public final String getScope() {
    return getScopeAnnotation().getName();
  }

  @Override
  public final @Nullable String getName() {
    return Arrays
        .stream(annotatedElement().getAnnotations())
        .filter(annotation -> annotation.annotationType() == Named.class)
        .map(annotation -> ((Named) annotation))
        .map(annotation -> annotation.value())
        .findAny()
        .orElse(null);
  }

  @Override
  public final Set<String> getQualifiers() {
    return getQualifierAnnotations()
        .stream()
        .map(annotation -> annotation.annotationType())
        .map(annotationType -> annotationType.getName())
        .collect(toImmutableSet());
  }

  @Override
  public final DescriptorType getDescriptorType() {
    return DescriptorType.CLASS;
  }

  @Override
  public final DescriptorVisibility getDescriptorVisibility() {
    return DescriptorVisibility.NORMAL;
  }

  @Override
  public final Map<String, List<String>> getMetadata() {
    return ImmutableMap.of();
  }

  @Override
  public final @Nullable HK2Loader getLoader() {
    return null;
  }

  @GuardedBy("this")
  private int ranking = 0;

  @GuardedBy("this")
  private boolean initialRankingFound = false;

  @Override
  public final synchronized int getRanking() {
    if (!initialRankingFound) {
      Rank rank = annotatedElement().getAnnotation(Rank.class);
      if (rank != null)
        ranking = rank.value();

      initialRankingFound = true;
    }

    return ranking;
  }

  @Override
  public final synchronized int setRanking(int ranking) {
    int previousRanking = getRanking();
    this.ranking = ranking;
    return previousRanking;
  }

  @Override
  public final @Nullable Boolean isProxiable() {
    UseProxy useProxy = annotatedElement().getAnnotation(UseProxy.class);
    return (useProxy == null) ? null : useProxy.value();
  }

  @Override
  public final @Nullable Boolean isProxyForSameScope() {
    ProxyForSameScope proxyForSameScope =
        annotatedElement().getAnnotation(ProxyForSameScope.class);
    return (proxyForSameScope == null) ? null : proxyForSameScope.value();
  }

  @Override
  public final @Nullable String getClassAnalysisName() {
    return null;
  }

  @Override
  public final @Nullable Long getServiceId() {
    return null;
  }

  @Override
  public final @Nullable Long getLocatorId() {
    return null;
  }

  @GuardedBy("this")
  private @Nullable Object cache = null;

  @GuardedBy("this")
  private boolean isCacheSet = false;

  @Override
  public final synchronized @Nullable Object getCache() {
    if (!isCacheSet)
      throw new IllegalStateException();

    return cache;
  }

  @Override
  public final synchronized boolean isCacheSet() {
    return isCacheSet;
  }

  @Override
  public final synchronized void setCache(Object cacheMe) {
    cache = cacheMe;
    isCacheSet = true;
  }

  @Override
  public final synchronized void releaseCache() {
    cache = null;
    isCacheSet = false;
  }
}
