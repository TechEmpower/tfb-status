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
 *
 * @param approach The approach used by this framework for implementing the test
 *        requirements, which is either "Realistic" or "Stripped".
 * @param classification The classification of this framework roughly describing
 *        the scope of functionality it provides for applications, which is
 *        "Fullstack", "Micro", or "Platform".
 * @param database The name of the database, or "None" if no database was used.
 *        Supported databases include "Postgres", "MySQL", and "MongoDB".
 * @param databaseOs The operating system of the database.  In practice this is
 *        always "Linux", but in theory this could be "Windows".
 * @param framework The name of the framework used by this framework
 *        permutation, such as the "gemini" framework for the "gemini-mysql" and
 *        "gemini-postgres" permutations.  This property's use of the term
 *        "framework" is inconsistent with the rest of this application, where
 *        "framework" refers to {@link #name()}.
 * @param language The programming language in which this framework's test
 *        implementations were written, such as "Java", "C#", or "TypeScript".
 * @param orm "Full", "Micro", or "Raw".
 * @param os The operating system of the application.  In practice this is
 *        always "Linux", but in theory this could be "Windows".
 * @param platform The name of the platform used by this application, such as
 *        "Servlet", ".NET", or "nodejs".
 * @param name The name of this framework permutation, such as "gemini-mysql".
 *        The rest of this application calls this the "framework", and the
 *        {@link #framework()} property of this class is an exception.
 * @param displayName The display name for this framework permutation as seen on
 *        the TFB website.  For example, the permutation named "aspnetcore-mw"
 *        may have a display name of "ASP.NET CORE [Middleware]".
 * @param notes Comments for the reader of the benchmark_config.json file, or
 *        the empty string if there are no comments.  This property is not used
 *        by the TFB website.
 * @param versus The name of the framework permutation against which this
 *        permutation should be compared in the "Framework overhead" tab on the
 *        TFB website, or "None" or the empty string if there is no suitable
 *        permutation for comparison.
 * @param webserver The name of the web server used by this framework
 *        permutation, such as "nginx", "Jetty", "Kestrel", or "uWSGI", or
 *        "None" if no web server is specified.
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
