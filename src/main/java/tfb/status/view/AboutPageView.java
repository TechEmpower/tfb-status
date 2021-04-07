package tfb.status.view;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * A view of the about page.
 *
 * @param gitProperties The content of the git.properties file from the
 *        classpath, or an empty list if that file is not present.  The
 *        git.properties file is generated when this application is packaged for
 *        deployment.  It contains information about the local git repository
 *        such as the current commit id.
 */
@Immutable
public record AboutPageView(ImmutableList<GitPropertyView> gitProperties) {

  public AboutPageView {
    Objects.requireNonNull(gitProperties);
  }

  /**
   * A single name + value pair in the git.properties file.
   *
   * @param name The name of the property, such as "git.branch".
   * @param value The value of the property.
   */
  @Immutable
  public record GitPropertyView(String name, String value) {

    public GitPropertyView {
      Objects.requireNonNull(name);
      Objects.requireNonNull(value);
    }
  }
}
