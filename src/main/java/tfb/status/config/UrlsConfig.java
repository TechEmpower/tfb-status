package tfb.status.config;

import java.util.Objects;
import javax.inject.Singleton;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import org.checkerframework.checker.nullness.qual.Nullable;

@Immutable
@Singleton
public class UrlsConfig {
  public final String tfbStatus;

  public final String teWeb;

  @JsonCreator
  public UrlsConfig(

      @JsonProperty(value = "tfbStatus", required = true)
      @Nullable String tfbStatus,

      @JsonProperty(value = "teWeb", required = true)
      @Nullable String teWeb) {

    this.tfbStatus = Objects.requireNonNullElse(tfbStatus, DEFAULT_TFB_STATUS);
    this.teWeb = Objects.requireNonNullElse(teWeb, DEFAULT_TE_WEB);
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

  private static final String DEFAULT_TFB_STATUS = "http://localhost:8080";
  private static final String DEFAULT_TE_WEB = "http://localhost:3000";
}
