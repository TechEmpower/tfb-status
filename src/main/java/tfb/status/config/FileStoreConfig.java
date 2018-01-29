package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * The configuration for miscellaneous files stored by the application.
 */
@Immutable
public final class FileStoreConfig {
  /**
   * The root directory for uploaded results files.
   */
  public final String resultsDirectory;

  /**
   * The root directory for user account files.
   */
  public final String accountsDirectory;

  /**
   * The root directory for tfb_lookup.json files.
   */
  public final String attributesDirectory;

  @JsonCreator
  public FileStoreConfig(

      @Nullable
      @JsonProperty(value = "resultsDirectory", required = false)
      String resultsDirectory,

      @Nullable
      @JsonProperty(value = "accountsDirectory", required = false)
      String accountsDirectory,

      @Nullable
      @JsonProperty(value = "attributesDirectory", required = false)
      String attributesDirectory) {

    this.resultsDirectory =
        Objects.requireNonNullElse(
            resultsDirectory,
            DEFAULT_RESULTS_DIRECTORY);

    this.accountsDirectory =
        Objects.requireNonNullElse(
            accountsDirectory,
            DEFAULT_ACCOUNTS_DIRECTORY);

    this.attributesDirectory =
        Objects.requireNonNullElse(
            attributesDirectory,
            DEFAULT_ATTRIBUTE_LOOKUP_DIRECTORY);
  }

  private static final String DEFAULT_RESULTS_DIRECTORY = "results";
  private static final String DEFAULT_ACCOUNTS_DIRECTORY = "accounts";
  private static final String DEFAULT_ATTRIBUTE_LOOKUP_DIRECTORY = "attributes";
}
