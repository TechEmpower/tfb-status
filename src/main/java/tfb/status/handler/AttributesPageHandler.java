package tfb.status.handler;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static io.undertow.util.StatusCodes.INTERNAL_SERVER_ERROR;
import static io.undertow.util.StatusCodes.NOT_FOUND;
import static io.undertow.util.StatusCodes.SERVICE_UNAVAILABLE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static tfb.status.undertow.extensions.RequestValues.queryParameter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Ascii;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import com.google.common.io.MoreFiles;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.hk2.extensions.Provides;
import tfb.status.service.FileStore;
import tfb.status.service.MustacheRenderer;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.util.ZipFiles;
import tfb.status.view.Attribute;
import tfb.status.view.AttributeInfo;
import tfb.status.view.AttributeLookup;
import tfb.status.view.AttributesJsonView;
import tfb.status.view.AttributesPageView;
import tfb.status.view.MinifiedTestDefinition;
import tfb.status.view.TestDefinition;

/**
 * Handles requests to preview the updated attribute definition and minified
 * test metadata for a run.
 */
@Singleton
public final class AttributesPageHandler implements HttpHandler {
  private final FileStore fileStore;
  private final MustacheRenderer mustacheRenderer;
  private final ObjectMapper objectMapper;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Inject
  public AttributesPageHandler(FileStore fileStore,
                               MustacheRenderer mustacheRenderer,
                               ObjectMapper objectMapper) {

    this.fileStore = Objects.requireNonNull(fileStore);
    this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  @Provides
  @Singleton
  @ExactPath("/attributes")
  public HttpHandler attributesPageHandler() {
    return HttpHandlers.chain(
        this,
        handler -> new MethodHandler().addMethod(GET, handler),
        handler -> new DisableCacheHandler(handler),
        handler -> new SetHeaderHandler(handler,
                                        ACCESS_CONTROL_ALLOW_ORIGIN,
                                        "*"));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws IOException {
    Path lookupFile =
        fileStore.attributesDirectory().resolve("tfb_lookup.json");

    if (!Files.isRegularFile(lookupFile)) {
      exchange.setStatusCode(SERVICE_UNAVAILABLE);
      return;
    }

    String zipFileName = queryParameter(exchange, "zipFile");
    if (zipFileName == null) {
      exchange.setStatusCode(BAD_REQUEST);
      return;
    }

    boolean isJson = "json".equals(queryParameter(exchange, "format"));

    Path requestedFile;
    try {
      requestedFile = fileStore.resultsDirectory().resolve(zipFileName);
    } catch (InvalidPathException ignored) {
      exchange.setStatusCode(NOT_FOUND);
      return;
    }

    if (!requestedFile.equals(requestedFile.normalize())
        || !requestedFile.startsWith(fileStore.resultsDirectory())) {
      exchange.setStatusCode(NOT_FOUND);
      return;
    }

    if (!Files.isRegularFile(requestedFile)
        || !MoreFiles.getFileExtension(requestedFile).equals("zip")) {
      exchange.setStatusCode(NOT_FOUND);
      return;
    }

    AttributeLookup lookup;
    try (InputStream inputStream = Files.newInputStream(lookupFile)) {
      lookup = objectMapper.readValue(inputStream, AttributeLookup.class);
    } catch (IOException e) {
      logger.warn("Exception thrown while reading tfb_lookup.json", e);
      exchange.setStatusCode(INTERNAL_SERVER_ERROR);
      return;
    }

    ImmutableList<TestDefinition> newTests =
        ZipFiles.readZipEntry(
            /* zipFile= */
            requestedFile,
            /* entryPath= */
            "test_metadata.json",
            /* entryReader= */
            inputStream ->
                objectMapper.readValue(
                    inputStream,
                    new TypeReference<ImmutableList<TestDefinition>>() {}));

    if (newTests == null || newTests.isEmpty()) {
      exchange.setStatusCode(NOT_FOUND);
      return;
    }

    // We convert the minified test definitions from tfb_lookup.js to full
    // test definitions, since this will make it easier to compare them to the
    // new test definitions.

    ImmutableMap<Integer, TestDefinition> oldTestMetadata =
        mapToComplete(lookup);

    ImmutableMap<Attribute, AttributeInfo> updatedAttributes =
        updateAttributes(lookup.attributes, newTests);

    ImmutableMap<String, MinifiedTestDefinition> updatedTestMetadata =
        minifyTestMetadata(updatedAttributes, newTests, oldTestMetadata);

    if (isJson) {

      var attributesJsonView =
          new AttributesJsonView(
              /* attributes= */ updatedAttributes,
              /* tests= */ updatedTestMetadata);

      String json = objectMapper.writeValueAsString(attributesJsonView);
      exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_UTF_8.toString());
      exchange.getResponseSender().send(json, UTF_8);

    } else { // HTML format

      String attributes = objectMapper.writeValueAsString(updatedAttributes);
      String tests = objectMapper.writeValueAsString(updatedTestMetadata);

      var attributesPageView =
          new AttributesPageView(
              /* attributes= */ attributes,
              /* tests= */ tests,
              /* fileName= */ requestedFile.getFileName().toString());

      String html = mustacheRenderer.render("attributes.mustache", attributesPageView);
      exchange.getResponseHeaders().put(CONTENT_TYPE, HTML_UTF_8.toString());
      exchange.getResponseSender().send(html, UTF_8);

    }
  }

  /**
   * Updates the list of attributes by comparing the list from each
   * attribute's {@link AttributeInfo} with all of the values found in the new
   * test definitions.  Any values that are not relevant for this run are
   * prefixed with {@link #UNUSED_MARKER}.
   *
   * @param previousAttributes the attribute definitions from the previous
   *        run, stored in tfb_lookup.json
   * @param testDefinitions the test_metadata.json file from the tfb run
   * @return the updated attribute definition
   */
  private static ImmutableMap<Attribute, AttributeInfo>
  updateAttributes(Map<Attribute, AttributeInfo> previousAttributes,
                   List<TestDefinition> testDefinitions) {

    var updatedAttributes = new ImmutableMap.Builder<Attribute, AttributeInfo>();

    ImmutableList<Map<Attribute, String>> attributeMaps =
        testDefinitions.stream()
                       .map(definition -> testDefinitionToMap(definition))
                       .collect(toImmutableList());

    previousAttributes.forEach(
        (Attribute attribute, AttributeInfo info) -> {

          ImmutableSet<String> allValues =
              attributeMaps.stream()
                           .map(testDefinition -> testDefinition.get(attribute))
                           .collect(toImmutableSet());

          ImmutableSet<String> oldValuesLower =
              info.list.stream()
                       .map(value -> Ascii.toLowerCase(value))
                       .collect(toImmutableSet());

          ImmutableSet<String> newValues =
              allValues.stream()
                       .filter(value -> !oldValuesLower.contains(Ascii.toLowerCase(value)))
                       .collect(toImmutableSet());

          ImmutableSet<String> newValuesLower =
              allValues.stream()
                       .map(value -> Ascii.toLowerCase(value))
                       .collect(toImmutableSet());

          ImmutableSet<String> unusedValues =
              info.list.stream()
                       .filter(value -> !newValuesLower.contains(Ascii.toLowerCase(value)))
                       .collect(toImmutableSet());

          ImmutableList<String> updatedList =
              Stream.concat(info.list.stream(), newValues.stream())
                    .map((String value) -> {
                      if (!unusedValues.contains(value) || value.startsWith(UNUSED_MARKER)) {
                        return value;
                      } else {
                        return UNUSED_MARKER + value;
                      }
                    })
                    .collect(toImmutableList());

          updatedAttributes.put(
              attribute,
              new AttributeInfo(
                  /* code= */ attribute.code(),
                  /* list= */ updatedList,
                  /* v= */ info.v));
        });

    return updatedAttributes.build();
  }

  /**
   * Any attribute values that are listed in tfb_lookup.json but are no longer
   * being used are prefixed with this.
   */
  private static final String UNUSED_MARKER = "-";

  /**
   * Maps the list of {@link TestDefinition} entries to {@link
   * MinifiedTestDefinition}.  This is done by getting the index of each
   * {@link Attribute} value from the corresponding
   * {@link AttributeInfo#list}.
   *
   * <p>For new tests, a new id is assigned.
   *
   * @param updatedAttributes the updated attribute definitions
   *        obtained from calling {@link #updateAttributes(Map, List)}
   * @param newTestDefinitions  then new test definitions
   * @param oldTestDefinitions the unminified test definitions from tfb_lookup
   * @return the new minified test definitions
   */
  private static ImmutableMap<String, MinifiedTestDefinition>
  minifyTestMetadata(Map<Attribute, AttributeInfo> updatedAttributes,
                     List<TestDefinition> newTestDefinitions,
                     Map<Integer, TestDefinition> oldTestDefinitions) {

    int nextIdentity = 1 + oldTestDefinitions.keySet()
                                             .stream()
                                             .mapToInt(identity -> identity)
                                             .max()
                                             .orElse(0);

    var testMetadata = new HashMap<Integer, TestDefinition>();

    for (TestDefinition newTest : newTestDefinitions) {
      Integer identity =
          oldTestDefinitions
              .entrySet()
              .stream()
              .filter(entry -> matchesOnAttributes(newTest, entry.getValue()))
              .map(entry -> entry.getKey())
              .findFirst()
              .orElse(null);

      // In some cases we might have multiple new tests that match to one of
      // the old tests; in these cases we simply assign them a new identity to
      // avoid overriding.
      if (identity == null || testMetadata.containsKey(identity)) {
        identity = nextIdentity++;
      }

      testMetadata.put(identity, newTest);
    }

    var attributeToValuesLower =
        new EnumMap<Attribute, List<String>>(Attribute.class);

    updatedAttributes.forEach(
        (Attribute attribute, AttributeInfo info) -> {
          attributeToValuesLower.put(
              attribute,
              info.list.stream()
                       .map(value -> Ascii.toLowerCase(value))
                       .collect(toImmutableList()));
        });

    var result = new ImmutableMap.Builder<String, MinifiedTestDefinition>();

    testMetadata.forEach(
        (Integer identity, TestDefinition definition) -> {

          String versusName = definition.versus;
          Integer versusIdentifier = null;

          if (versusName != null && !versusName.isEmpty()) {
            versusIdentifier =
                testMetadata
                    .entrySet()
                    .stream()
                    .filter(entry -> {
                      TestDefinition other = entry.getValue();
                      return versusName.equalsIgnoreCase(other.framework)
                          || versusName.equalsIgnoreCase(other.name);
                    })
                    .map(entry -> entry.getKey())
                    .findFirst()
                    .orElse(null);
          }

          var indexes = new EnumMap<Attribute, String>(Attribute.class);

          testDefinitionToMap(definition).forEach(
              (Attribute attribute, String value) -> {
                if (attribute == Attribute.NAME) {
                  // Test names are copied as is.
                  indexes.put(attribute, value);
                } else {
                  List<String> values = attributeToValuesLower.get(attribute);
                  if (values != null) {
                    int index = values.indexOf(Ascii.toLowerCase(value));
                    indexes.put(attribute, String.valueOf(index));
                  }
                }
              });

          result.put(
              String.valueOf(identity),
              new MinifiedTestDefinition(
                  /* approach= */
                  indexes.getOrDefault(Attribute.APPROACH, ""),
                  /* classification= */
                  indexes.getOrDefault(Attribute.CLASSIFICATION, ""),
                  /* database= */
                  indexes.getOrDefault(Attribute.DATABASE, ""),
                  /* databaseOs= */
                  indexes.getOrDefault(Attribute.DATABASE_OS, ""),
                  /* framework= */
                  indexes.getOrDefault(Attribute.FRAMEWORK, ""),
                  /* language= */
                  indexes.getOrDefault(Attribute.LANGUAGE, ""),
                  /* orm= */
                  indexes.getOrDefault(Attribute.ORM, ""),
                  /* os= */
                  indexes.getOrDefault(Attribute.OS, ""),
                  /* platform= */
                  indexes.getOrDefault(Attribute.PLATFORM, ""),
                  /* name= */
                  indexes.getOrDefault(Attribute.NAME, ""),
                  /* displayName= */
                  indexes.getOrDefault(Attribute.DISPLAY_NAME, ""),
                  /* webServer= */
                  indexes.getOrDefault(Attribute.WEBSERVER, ""),
                  /* identity= */
                  identity,
                  /* v= */
                  (versusIdentifier == null)
                      ? ImmutableList.of()
                      : ImmutableList.of(versusIdentifier)));
        });

    return result.build();
  }

  private static boolean matchesOnAttributes(TestDefinition a,
                                             TestDefinition b) {
    return a.name.equalsIgnoreCase(b.name)
        || (a.approach.equalsIgnoreCase(b.approach)
            && a.classification.equalsIgnoreCase(b.classification)
            && a.database.equalsIgnoreCase(b.database)
            && a.framework.equalsIgnoreCase(b.framework)
            && a.language.equalsIgnoreCase(b.language)
            && a.orm.equalsIgnoreCase(b.orm)
            && a.platform.equalsIgnoreCase(b.platform)
            && a.webserver.equalsIgnoreCase(b.webserver));
  }

  private static ImmutableMap<Attribute, String>
  testDefinitionToMap(TestDefinition definition) {
    return new ImmutableMap.Builder<Attribute, String>()
        .put(Attribute.APPROACH, definition.approach)
        .put(Attribute.CLASSIFICATION, definition.classification)
        .put(Attribute.DATABASE, definition.database)
        .put(Attribute.DATABASE_OS, definition.databaseOs)
        .put(Attribute.FRAMEWORK, definition.framework)
        .put(Attribute.LANGUAGE, definition.language)
        .put(Attribute.NAME, definition.name)
        .put(Attribute.ORM, definition.orm)
        .put(Attribute.OS, definition.os)
        .put(Attribute.PLATFORM, definition.platform)
        .put(Attribute.WEBSERVER, definition.webserver)
        .build();
  }

  /**
   * Unminifies the test definitions from an {@link AttributeLookup} object.
   *
   * @param lookup the attributes and minified tests
   * @return a map of each test definition to its identity
   */
  private static ImmutableMap<Integer, TestDefinition>
  mapToComplete(AttributeLookup lookup) {

    var values = new ValuesByAttributeAndIndex(lookup);

    var result = new ImmutableMap.Builder<Integer, TestDefinition>();

    lookup.minifiedTests.forEach(
        (String identity, MinifiedTestDefinition minifiedTest) -> {

          String versus;
          if (minifiedTest.v.isEmpty()) {
            versus = "";
          } else {
            MinifiedTestDefinition versusTest =
                lookup.minifiedTests.get(
                    String.valueOf(minifiedTest.v.get(0)));

            versus = (versusTest == null) ? "" : versusTest.name;
          }

          result.put(
              Integer.parseInt(identity),
              new TestDefinition(
                  /* approach= */
                  values.get(Attribute.APPROACH, minifiedTest.approach),
                  /* classification= */
                  values.get(Attribute.CLASSIFICATION, minifiedTest.classification),
                  /* database= */
                  values.get(Attribute.DATABASE, minifiedTest.database),
                  /* databaseOs= */
                  values.get(Attribute.DATABASE_OS, minifiedTest.databaseOs),
                  /* framework= */
                  values.get(Attribute.FRAMEWORK, minifiedTest.framework),
                  /* language= */
                  values.get(Attribute.LANGUAGE, minifiedTest.language),
                  /* orm= */
                  values.get(Attribute.ORM, minifiedTest.orm),
                  /* os= */
                  values.get(Attribute.OS, minifiedTest.os),
                  /* platform= */
                  values.get(Attribute.PLATFORM, minifiedTest.platform),
                  /* name= */
                  minifiedTest.name,
                  /* displayName= */
                  (minifiedTest.displayName == null) ? "" : minifiedTest.displayName,
                  /* notes= */
                  "",
                  /* versus= */
                  versus,
                  /* webserver= */
                  values.get(Attribute.WEBSERVER, minifiedTest.webServer)));
        });

    return result.build();
  }

  private static final class ValuesByAttributeAndIndex {
    private final Table<Attribute, String, String> table = HashBasedTable.create();

    ValuesByAttributeAndIndex(AttributeLookup lookup) {
      lookup.attributes.forEach(
          (Attribute attribute, AttributeInfo info) -> {
            for (int index = 0; index < info.list.size(); index++) {
              table.put(
                  attribute,
                  String.valueOf(index),
                  info.list.get(index));
            }
          });
    }

    String get(Attribute attribute, String index) {
      String value = table.get(attribute, index);
      return (value == null) ? "" : value;
    }
  }
}
