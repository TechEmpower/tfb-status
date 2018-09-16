package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.io.ByteSource;
import com.google.common.io.MoreFiles;
import com.google.common.net.HostAndPort;
import com.google.errorprone.annotations.Immutable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

/**
 * The parent configuration object for this entire application, containing all
 * other component-specific configuration objects.
 *
 * <p>This class provides factory methods for reading configuration from a YAML
 * file.  See {@link #readYamlFile(String)} and {@link
 * #readYamlBytes(ByteSource)}.
 */
@Immutable
@Singleton
public final class ApplicationConfig {
  public final HttpServerConfig http;
  public final AssetsConfig assets;
  public final MustacheConfig mustache;
  public final FileStoreConfig fileStore;

  /**
   * The configuration for outbound emails, or {@code null} if outbound emails
   * are disabled.
   */
  @Nullable public final EmailConfig email;

  @JsonCreator
  public ApplicationConfig(

      @Nullable
      @JsonProperty(value = "http", required = false)
      HttpServerConfig http,

      @Nullable
      @JsonProperty(value = "assets", required = false)
      AssetsConfig assets,

      @Nullable
      @JsonProperty(value = "mustache", required = false)
      MustacheConfig mustache,

      @Nullable
      @JsonProperty(value = "fileStore", required = false)
      FileStoreConfig fileStore,

      @Nullable
      @JsonProperty(value = "email", required = false)
      EmailConfig email) {

    this.http =
        Objects.requireNonNullElseGet(
            http,
            () -> new HttpServerConfig(null, null, null));

    this.assets =
        Objects.requireNonNullElseGet(
            assets,
            () -> new AssetsConfig(null, null));

    this.mustache =
        Objects.requireNonNullElseGet(
            mustache,
            () -> new MustacheConfig(null, null));

    this.fileStore =
        Objects.requireNonNullElseGet(
            fileStore,
            () -> new FileStoreConfig(null, null, null, null));

    this.email = email;
  }

  /**
   * Reads this application's configuration from a YAML file on disk.
   *
   * @param yamlFilePath the path to the YAML configuration file for this
   *        application
   * @return the configuration object for this application
   * @throws InvalidConfigFileException if there is a problem reading the
   *         configuration file
   */
  public static ApplicationConfig readYamlFile(String yamlFilePath) {
    Objects.requireNonNull(yamlFilePath);

    Path yamlFile;
    try {
      yamlFile = Paths.get(yamlFilePath);
    } catch (InvalidPathException e) {
      throw new InvalidConfigFileException(
          "The specified path for the configuration file, \""
              + yamlFilePath
              + "\", is invalid according to the host file system",
          e);
    }

    if (!Files.isRegularFile(yamlFile))
      throw new InvalidConfigFileException(
          "The specified path for the configuration file, \""
              + yamlFilePath
              + "\", does not point to an existing file");

    return readYamlBytes(MoreFiles.asByteSource(yamlFile));
  }

  /**
   * Reads this application's configuration from the raw bytes of a YAML file.
   *
   * @param yamlBytes the raw bytes of the YAML configuration file for this
   *        application
   * @return the configuration object for this application
   * @throws InvalidConfigFileException if there is a problem reading the
   *         configuration file bytes
   */
  public static ApplicationConfig readYamlBytes(ByteSource yamlBytes) {
    Objects.requireNonNull(yamlBytes);

    var objectMapper = new ObjectMapper(new YAMLFactory());

    ApplicationConfig config;
    try (InputStream inputStream = yamlBytes.openStream()) {
      config = objectMapper.readValue(inputStream, ApplicationConfig.class);
    } catch (IOException e) {
      throw new InvalidConfigFileException(
          "Unable to create configuration object from bytes, "
              + "byte source: " + yamlBytes, // probably has a useful toString()
          e);
    }

    verifyDirectory(
        "fileStore.resultsDirectory",
        config.fileStore.resultsDirectory);

    verifyDirectory(
        "fileStore.accountsDirectory",
        config.fileStore.accountsDirectory);

    verifyHostAndPort(
        "http.host",
        "http.port",
        config.http.host,
        config.http.port);

    if (config.email != null) {
      verifyHostAndPort(
          "email.host",
          "email.port",
          config.email.host,
          config.email.port);

      verifyEmailAddress(
          "email.from",
          config.email.from);

      verifyEmailAddress(
          "email.to",
          config.email.to);
    }

    return config;
  }

  /**
   * Verifies that the given path is a directory already or that it can be
   * created as a directory.
   *
   * @throws InvalidConfigFileException if the directory path is invalid
   */
  private static void verifyDirectory(String propertyName,
                                      String directoryPath) {

    Objects.requireNonNull(propertyName);
    Objects.requireNonNull(directoryPath);

    Path directory;
    try {
      directory = Paths.get(directoryPath);
    } catch (InvalidPathException e) {
      throw new InvalidConfigFileException(
          propertyName + ": invalid path \"" + directoryPath + "\"",
          e);
    }

    if (Files.exists(directory)) {
      if (Files.isDirectory(directory)) {
        return;
      } else {
        throw new InvalidConfigFileException(
            propertyName
                + ": the path \""
                + directoryPath
                + "\" exists but is not a directory");
      }
    }

    for (Path parent = directory.getParent();
         parent != null;
         parent = parent.getParent()) {
      if (Files.exists(parent)) {
        if (Files.isDirectory(parent)) {
          return;
        } else {
          throw new InvalidConfigFileException(
              propertyName
                  + ": the path \""
                  + directoryPath
                  + "\" has a non-directory parent \""
                  + parent
                  + "\"");
        }
      }
    }
  }

  /**
   * Verifies that the given host string has valid syntax, that the given port
   * number is theoretically valid (that it is in the range {@code [0,65535]}),
   * and that the host string does not itself contain a port number.
   *
   * @throws InvalidConfigFileException if the host or port is invalid
   */
  private static void verifyHostAndPort(String hostPropertyName,
                                        String portPropertyName,
                                        String host,
                                        int port) {

    Objects.requireNonNull(hostPropertyName);
    Objects.requireNonNull(portPropertyName);
    Objects.requireNonNull(host);

    try {
      HostAndPort.fromParts(host, port);
    } catch (IllegalArgumentException e) {
      throw new InvalidConfigFileException(
          hostPropertyName
              + ", "
              + portPropertyName
              + ": invalid host and/or port, host="
              + host
              + ", port="
              + port,
          e);
    }
  }

  /**
   * Verifies that the given email address has valid syntax.
   *
   * @throws InvalidConfigFileException if the email address is invalid
   */
  private static void verifyEmailAddress(String propertyName,
                                         String emailAddress) {

    Objects.requireNonNull(propertyName);
    Objects.requireNonNull(emailAddress);

    // Use the same parsing algorithm here that we'll use later, at the point of
    // actually sending emails.
    try {
      new InternetAddress(emailAddress);
    } catch (AddressException e) {
      throw new InvalidConfigFileException(
          propertyName + ": invalid email address " + emailAddress,
          e);
    }
  }

  /**
   * An exception thrown when this application's configuration file cannot be
   * deserialized into an {@link ApplicationConfig} object for any reason.
   *
   * <p>Possible reasons for this exception to be thrown include, but are not
   * limited to:
   *
   * <ul>
   * <li>The configuration file does not exist.
   * <li>The configuration file is not in a supported format.
   * <li>The configuration file is missing required properties.
   * </ul>
   */
  public static final class InvalidConfigFileException
      extends RuntimeException {

    /**
     * Constructs a new invalid config file exception with the provided message.
     *
     * @param message the error message
     */
    public InvalidConfigFileException(String message) {
      super(Objects.requireNonNull(message));
    }

    /**
     * Constructs a new invalid config file exception with the provided message
     * and cause.
     *
     * @param message the error message
     * @param cause the cause of this exception
     */
    public InvalidConfigFileException(String message, Throwable cause) {
      super(Objects.requireNonNull(message),
            Objects.requireNonNull(cause));
    }

    private static final long serialVersionUID = 0;
  }
}
