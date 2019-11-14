package tfb.status.view;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * JSON properties that are used in both {@link TestDefinition} as well as
 * {@link MinifiedTestDefinition}.
 *
 * <p>Note that not all are present in both, and the code is typically used in
 * the minified version, while the name is used in the non-minified version.
 */
public enum Attribute {
  APPROACH("approach", "a"),
  CLASSIFICATION("classification", "c"),
  DATABASE("database", "d"),
  DATABASE_OS("database_os", "b"),
  FRAMEWORK("framework", "f"),
  LANGUAGE("language", "l"),
  ORM("orm", "o"),
  OS("os", "s"),
  PLATFORM("platform", "p"),
  WEBSERVER("webserver", "w"),

  // A test's name.  This is typically set to either the framework or the
  // language.
  NAME("name", "i"),

  // These are excluded from minified test definitions.
  NOTES("notes", "n"),
  VERSUS("versus", "v"),

  // These are only present in minified test definitions.
  INDEX("index", "ii"),
  DISPLAY_NAME("display_name", "t");

  // The string used as a key in a non-minified test metadata entry.
  private final String testMetadataKey;

  // The corresponding code used as a key in a minified test metadata entry.
  private final String code;

  Attribute(String testMetadataKey, String code) {
    this.testMetadataKey = testMetadataKey;
    this.code = code;
  }

  @JsonValue
  public String testMetadataKey() {
    return testMetadataKey;
  }

  public String code() {
    return code;
  }

  @JsonCreator
  public static @Nullable Attribute fromTestMetadataKey(String testMetadataKey) {
    Objects.requireNonNull(testMetadataKey);
    return Arrays.stream(Attribute.values())
                 .filter(attribute -> attribute.testMetadataKey.equals(testMetadataKey))
                 .findFirst()
                 .orElse(null);
  }
}
