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
public record TestDefinition(

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

  @JsonCreator
  public TestDefinition {
    Objects.requireNonNull(approach);
    Objects.requireNonNull(classification);
    Objects.requireNonNull(database);
    Objects.requireNonNull(databaseOs);
    Objects.requireNonNull(framework);
    Objects.requireNonNull(language);
    Objects.requireNonNull(orm);
    Objects.requireNonNull(os);
    Objects.requireNonNull(platform);
    Objects.requireNonNull(name);
    Objects.requireNonNull(displayName);
    Objects.requireNonNull(notes);
    Objects.requireNonNull(webserver);
  }
}
