package tfb.status.view;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A minified view of a {@link TestDefinition} for consumption by the TFB
 * website.
 *
 * <p>Note: Even though the fields are strings, most are actually integers that
 * correspond to the index of their actual value from a {@link
 * AttributeInfo#list}.  The only exceptions are the name and display name,
 * which are non-numeric strings.
 */
@Immutable
public final class MinifiedTestDefinition {
  @JsonProperty("ii")
  public final int identity;

  @JsonProperty("v")
  public final ImmutableList<Integer> v;

  @JsonProperty("a")
  public final String approach;

  @JsonProperty("c")
  public final String classification;

  @JsonProperty("d")
  public final String database;

  @JsonProperty("b")
  public final String databaseOs;

  @JsonProperty("f")
  public final String framework;

  @JsonProperty("l")
  public final String language;

  @JsonProperty("o")
  public final String orm;

  @JsonProperty("s")
  public final String os;

  @JsonProperty("p")
  public final String platform;

  @JsonProperty("w")
  public final String webServer;

  // ------------------------
  // These fields are non-numeric strings, unlike the rest which are integers.
  @JsonProperty("i")
  public final String name;

  @JsonProperty("t")
  public final @Nullable String displayName;
  // ------------------------

  @JsonCreator
  public MinifiedTestDefinition(

      @JsonProperty(value = "a", required = true)
      String approach,

      @JsonProperty(value = "c", required = true)
      String classification,

      @JsonProperty(value = "d", required = true)
      String database,

      @JsonProperty(value = "b", required = true)
      String databaseOs,

      @JsonProperty(value = "f", required = true)
      String framework,

      @JsonProperty(value = "l", required = true)
      String language,

      @JsonProperty(value = "o", required = true)
      String orm,

      @JsonProperty(value = "s", required = true)
      String os,

      @JsonProperty(value = "p", required = true)
      String platform,

      @JsonProperty(value = "i", required = true)
      String name,

      @Nullable
      @JsonProperty(value = "t", required = false)
      String displayName,

      @JsonProperty(value = "w", required = true)
      String webServer,

      @JsonProperty(value = "ii", required = true)
      int identity,

      @JsonProperty(value = "v", required = true)
      ImmutableList<Integer> v) {

    this.identity = identity;
    this.v = Objects.requireNonNull(v);
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
    this.displayName = displayName;
    this.webServer = Objects.requireNonNull(webServer);
  }
}
