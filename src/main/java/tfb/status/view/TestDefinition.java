package tfb.status.view;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A framework permutation that is a top-level entry in test_metadata.json.
 * There is one of these entries in test_metadata.json for each top-level entry
 * in the main TFB project's benchmark_config.json files.  For example, for the
 * Undertow framework, there may be multiple test definitions whose {@link
 * #name} attributes are "undertow", "undertow-mysql", and
 * "undertow-postgresql", representing different configurations of the same
 * framework.
 */
@Immutable
public final class TestDefinition {
  public final String approach;
  public final String classification;
  public final String database;
  public final String databaseOs;
  public final String framework;
  public final String language;
  public final String orm;
  public final String os;
  public final String platform;
  public final String displayName;
  public final String name;
  public final String notes;
  public final @Nullable String versus;
  public final String webserver;

  @JsonCreator
  public TestDefinition(

      @JsonProperty(value = "approach", required = true)
      String approach,

      @JsonProperty(value = "classification", required = true)
      String classification,

      @JsonProperty(value = "database", required = true)
      String database,

      @JsonProperty(value = "database_os", required = true)
      String databaseOs,

      @JsonProperty(value = "framework", required = true)
      String framework,

      @JsonProperty(value = "language", required = true)
      String language,

      @JsonProperty(value = "orm", required = true)
      String orm,

      @JsonProperty(value = "os", required = true)
      String os,

      @JsonProperty(value = "platform", required = true)
      String platform,

      @JsonProperty(value = "name", required = true)
      String name,

      @JsonProperty(value = "display_name", required = true)
      String displayName,

      @JsonProperty(value = "notes", required = true)
      String notes,

      @JsonProperty(value = "versus", required = false)
      @Nullable String versus,

      @JsonProperty(value = "webserver", required = true)
      String webserver) {

    this.approach = Objects.requireNonNull(approach);
    this.classification = Objects.requireNonNull(classification);
    this.database = Objects.requireNonNull(database);
    this.databaseOs = Objects.requireNonNull(databaseOs);
    this.framework = Objects.requireNonNull(framework);
    this.language = Objects.requireNonNull(language);
    this.orm = Objects.requireNonNull(orm);
    this.os = Objects.requireNonNull(os);
    this.platform = Objects.requireNonNull(platform);
    this.name = Objects.requireNonNull(name);
    this.displayName = Objects.requireNonNull(displayName);
    this.notes = Objects.requireNonNull(notes);
    this.versus = versus;
    this.webserver = Objects.requireNonNull(webserver);
  }
}
