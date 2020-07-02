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
  @JsonProperty("database_os")
  public final String databaseOs;
  public final String framework;
  public final String language;
  public final String orm;
  public final String os;
  public final String platform;
  @JsonProperty("display_name")
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

  @Override
  public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    } else if (!(object instanceof TestDefinition)) {
      return false;
    } else {
      TestDefinition that = (TestDefinition) object;
      return this.approach.equals(that.approach)
          && this.classification.equals(that.classification)
          && this.database.equals(that.database)
          && this.databaseOs.equals(that.databaseOs)
          && this.framework.equals(that.framework)
          && this.language.equals(that.language)
          && this.orm.equals(that.orm)
          && this.os.equals(that.os)
          && this.platform.equals(that.platform)
          && this.displayName.equals(that.displayName)
          && this.name.equals(that.name)
          && this.notes.equals(that.notes)
          && Objects.equals(this.versus, that.versus)
          && this.webserver.equals(that.webserver);
    }
  }

  @Override
  public int hashCode() {
    int hash = 1;
    hash = 31 * hash + approach.hashCode();
    hash = 31 * hash + classification.hashCode();
    hash = 31 * hash + database.hashCode();
    hash = 31 * hash + databaseOs.hashCode();
    hash = 31 * hash + framework.hashCode();
    hash = 31 * hash + language.hashCode();
    hash = 31 * hash + orm.hashCode();
    hash = 31 * hash + os.hashCode();
    hash = 31 * hash + platform.hashCode();
    hash = 31 * hash + displayName.hashCode();
    hash = 31 * hash + name.hashCode();
    hash = 31 * hash + notes.hashCode();
    hash = 31 * hash + Objects.hashCode(versus);
    hash = 31 * hash + webserver.hashCode();
    return hash;
  }
}
