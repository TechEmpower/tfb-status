package tfb.status.view;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * A view of the about page.
 */
@Immutable
public final class AboutPageView {
  /**
   * The amount of time this application instance has been running, as human
   * readable text.
   */
  public final String uptime;

  /**
   * The content of the git.properties file from the classpath, or an empty list
   * if that file is not present.
   *
   * <p>The git.properties file is generated when the application is packaged
   * for deployment.  It contains information about the local git repository
   * such as the current commit id.
   */
  public final ImmutableList<GitPropertyView> gitProperties;

  public AboutPageView(String uptime,
                       ImmutableList<GitPropertyView> gitProperties) {
    this.uptime = Objects.requireNonNull(uptime);
    this.gitProperties = Objects.requireNonNull(gitProperties);
  }

  /**
   * A single name + value pair in the git.properties file.
   */
  @Immutable
  public static final class GitPropertyView {
    /**
     * The name of the property, such as "git.branch".
     */
    public final String name;

    /**
     * The value of the property.
     */
    public final String value;

    public GitPropertyView(String name, String value) {
      this.name = Objects.requireNonNull(name);
      this.value = Objects.requireNonNull(value);
    }
  }
}
