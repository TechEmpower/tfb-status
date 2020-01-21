package tfb.status.hk2.extensions;

import static org.glassfish.hk2.utilities.ServiceLocatorUtilities.createAndPopulateServiceLocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.junit.jupiter.api.Test;
import org.jvnet.hk2.annotations.Contract;

/**
 * Tests for {@link NoInstancesFilter}.
 */
public final class NoInstancesFilterTest {
  /**
   * Verifies that a service class may provide itself by way of a static {@link
   * Provides} field even when the class has no constructors compatible with
   * injection.
   */
  @Test
  public void testServiceProvidesItselfFromField() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    NoInstancesFilter.enableNoInstancesFilter(locator);
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesSelfFromField.class);

    ProvidesSelfFromField self =
        locator.getService(ProvidesSelfFromField.class);

    assertNotNull(self);
    assertNotNull(self.nonService);
    assertEquals("hey", self.nonService.message);
  }

  /**
   * Verifies that a service class may provide itself by way of a static {@link
   * Provides} method even when the class has no constructors compatible with
   * injection.
   */
  @Test
  public void testServiceProvidesItselfFromMethod() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    NoInstancesFilter.enableNoInstancesFilter(locator);
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ParamForProvides1.class,
        ParamForProvides2.class,
        ProvidesSelfFromMethod.class);

    ProvidesSelfFromMethod self =
        locator.getService(ProvidesSelfFromMethod.class);

    assertNotNull(self);
    assertNotNull(self.nonService);
    assertEquals("hi", self.nonService.message);
    assertNotNull(self.otherService1);
    assertNotNull(self.otherService2);
  }

  /**
   * Verifies that a utility class may be registered as a service when it has
   * static methods or fields annotated with {@link Provides}, and verifies that
   * the utility class itself cannot be fetched as a service.
   */
  @Test
  public void testUtilityClassProvides() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    NoInstancesFilter.enableNoInstancesFilter(locator);
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        UtilityClassProvides.class);

    assertNotNull(locator.getService(FromUtilityClassMethod.class));
    assertNotNull(locator.getService(FromUtilityClassField.class));
    assertNull(locator.getService(UtilityClassProvides.class));
  }

  /**
   * Verifies that an abstract class may be registered as a service when it has
   * static methods or fields annotated with {@link Provides}, and verifies that
   * the abstract class itself cannot be fetched as a service.
   */
  @Test
  public void testAbstractClassProvides() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    NoInstancesFilter.enableNoInstancesFilter(locator);
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        AbstractClassProvides.class);

    assertNotNull(locator.getService(FromAbstractClassMethod.class));
    assertNotNull(locator.getService(FromAbstractClassField.class));
    assertNull(locator.getService(AbstractClassProvides.class));
  }

  /**
   * Verifies that an interface may be registered as a service when it has
   * static methods or fields annotated with {@link Provides}, and verifies that
   * the interface itself cannot be fetched as a service.
   */
  @Test
  public void testInterfaceProvides() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    NoInstancesFilter.enableNoInstancesFilter(locator);
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        InterfaceProvides.class);

    assertNotNull(locator.getService(FromInterfaceMethod.class));
    assertNotNull(locator.getService(FromInterfaceField.class));
    assertNull(locator.getService(InterfaceProvides.class));
  }

  /**
   * Verifies that an enum class may be registered as a service when its enum
   * constants are annotated with {@link Provides}.
   */
  @Test
  public void testEnumProvides() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    NoInstancesFilter.enableNoInstancesFilter(locator);
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        EnumProvides.class);

    List<EnumProvides> services =
        locator.getAllServices(EnumProvides.class);

    Set<EnumProvides> expected = EnumSet.allOf(EnumProvides.class);

    assertEquals(expected.size(), services.size());

    assertEquals(
        expected,
        EnumSet.copyOf(services));
  }

  /**
   * Verifies that contracts of enum constants annotated with {@link Provides}
   * are detected correctly.
   */
  @Test
  public void testEnumProvidesContracts() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    NoInstancesFilter.enableNoInstancesFilter(locator);
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        EnumProvidesContract.class);

    List<EnumContract> services =
        locator.getAllServices(EnumContract.class);

    Set<EnumProvidesContract> expected =
        EnumSet.allOf(EnumProvidesContract.class);

    assertEquals(expected.size(), services.size());

    assertEquals(
        expected,
        new HashSet<>(services));

    List<SecondEnumContract> secondServices =
        locator.getAllServices(SecondEnumContract.class);

    assertEquals(expected.size(), secondServices.size());

    assertEquals(
        expected,
        new HashSet<>(secondServices));
  }

  public static final class ParamForProvides1 {}
  public static final class ParamForProvides2 {}

  public static final class ExoticNonServiceType {
    public final String message;

    ExoticNonServiceType(String message) {
      this.message = Objects.requireNonNull(message);
    }
  }

  public static final class ProvidesSelfFromMethod {
    public final ExoticNonServiceType nonService;
    public final ParamForProvides1 otherService1;
    public final ParamForProvides2 otherService2;

    private ProvidesSelfFromMethod(ExoticNonServiceType nonService,
                                   ParamForProvides1 otherService1,
                                   ParamForProvides2 otherService2) {
      this.nonService = Objects.requireNonNull(nonService);
      this.otherService1 = Objects.requireNonNull(otherService1);
      this.otherService2 = Objects.requireNonNull(otherService2);
    }

    @Provides
    public static ProvidesSelfFromMethod create(ParamForProvides1 otherService1,
                                                ParamForProvides2 otherService2) {
      return new ProvidesSelfFromMethod(
          new ExoticNonServiceType("hi"),
          otherService1,
          otherService2);
    }
  }

  public static final class ProvidesSelfFromField {
    public final ExoticNonServiceType nonService;

    private ProvidesSelfFromField(ExoticNonServiceType nonService) {
      this.nonService = Objects.requireNonNull(nonService);
    }

    @Provides
    public static final ProvidesSelfFromField instance =
        new ProvidesSelfFromField(new ExoticNonServiceType("hey"));
  }

  public static final class UtilityClassProvides {
    private UtilityClassProvides() {
      throw new AssertionError("This class cannot be instantiated");
    }

    @Provides
    public static FromUtilityClassMethod method() {
      return new FromUtilityClassMethod();
    }

    @Provides
    public static final FromUtilityClassField field =
        new FromUtilityClassField();
  }

  public static final class FromUtilityClassMethod {}
  public static final class FromUtilityClassField {}

  public abstract static class AbstractClassProvides {
    // Dodge complaints from static analysis about this class.
    abstract String dummy();

    @Provides
    public static FromAbstractClassMethod method() {
      return new FromAbstractClassMethod();
    }

    @Provides
    public static final FromAbstractClassField field =
        new FromAbstractClassField();
  }

  public static final class FromAbstractClassMethod {}
  public static final class FromAbstractClassField {}

  public interface InterfaceProvides {
    // Dodge complaints from static analysis about this interface.
    String dummy();

    @Provides
    static FromInterfaceMethod method() {
      return new FromInterfaceMethod();
    }

    @Provides
    FromInterfaceField field = new FromInterfaceField();
  }

  public static final class FromInterfaceMethod {}
  public static final class FromInterfaceField {}

  public enum EnumProvides {
    @Provides
    FOO,

    @Provides
    BAR {
      // Have a class body, which subclasses the enum type, so that we can
      // verify that all of the enum constants still provide the same contract.
      @Override
      public String toString() {
        return "bar";
      }
    }
  }

  @Contract
  public interface EnumContract {}

  @Contract
  public interface SecondEnumContract {}

  public enum EnumProvidesContract implements EnumContract, SecondEnumContract {
    @Provides
    FOO,

    @Provides
    BAR {
      // Have a class body, which subclasses the enum type, so that we can
      // verify that all of the enum constants still provide the same contract.
      @Override
      public String toString() {
        return "bar";
      }
    }
  }
}
