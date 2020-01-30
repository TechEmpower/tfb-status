package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Configuration for various URLs that the application needs to know about.
 * These are generally used to generate fully qualified URLs pointing to this
 * or other sites.
 */
@Immutable
@Singleton
public final class UrlsConfig {
  /**
   * The origin of this application, containing the protocol and domain.
   * Should not end with a slash.
   */
  public final String tfbStatus;

  /**
   * The origin of the TechEmpower website, containing the protocol and domain.
   * Should not end with a slash.
   */
  public final String teWeb;

  public UrlsConfig(String tfbStatus, String teWeb) {
    this.tfbStatus = Objects.requireNonNull(tfbStatus);
    this.teWeb = Objects.requireNonNull(teWeb);
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) {
      return true;
    } else if (!(object instanceof UrlsConfig)) {
      return false;
    } else {
      UrlsConfig that = (UrlsConfig) object;
      return this.tfbStatus.equals(that.tfbStatus)
          && this.teWeb.equals(that.teWeb);
    }
  }

  @Override
  public int hashCode() {
    int hash = 1;
    hash = 31 * hash + tfbStatus.hashCode();
    hash = 31 * hash + teWeb.hashCode();
    return hash;
  }

  private static final String DEFAULT_TFB_STATUS =
      "https://tfb-status.techempower.com";
  private static final String DEFAULT_TE_WEB = "https://www.techempower.com";

  @JsonCreator
  public static UrlsConfig create(
      @JsonProperty(value = "tfbStatus", required = false)
      @Nullable String tfbStatus,

      @JsonProperty(value = "teWeb", required = false)
      @Nullable String teWeb) {
    return new UrlsConfig(
        /* tfbStatus= */
        Objects.requireNonNullElse(tfbStatus, DEFAULT_TFB_STATUS),

        /* teWeb= */
        Objects.requireNonNullElse(teWeb, DEFAULT_TE_WEB));
  }

  public static UrlsConfig defaultConfig() {
    return create(null, null);
  }
}
