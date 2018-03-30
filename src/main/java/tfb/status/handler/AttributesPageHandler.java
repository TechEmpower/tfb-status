package tfb.status.handler;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
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
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import tfb.status.config.FileStoreConfig;
import tfb.status.service.Authenticator;
import tfb.status.service.MustacheRenderer;
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
  private final HttpHandler delegate;

  @Inject
  public AttributesPageHandler(FileStoreConfig fileStoreConfig,
                               MustacheRenderer mustacheRenderer,
                               Authenticator authenticator,
                               ObjectMapper objectMapper) {

    HttpHandler handler = new CoreHandler(fileStoreConfig,
                                          mustacheRenderer,
                                          objectMapper);

    handler = new MethodHandler().addMethod(GET, handler);
    handler = new DisableCacheHandler(handler);
    handler = new SetHeaderHandler(handler, ACCESS_CONTROL_ALLOW_ORIGIN, "*");

    delegate = handler;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }

  private static final class CoreHandler implements HttpHandler {
    private final Path resultsDirectory;
    private final Path attributesDirectory;
    private final ObjectMapper objectMapper;
    private final MustacheRenderer mustacheRenderer;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    CoreHandler(FileStoreConfig fileStoreConfig,
                MustacheRenderer mustacheRenderer,
                ObjectMapper objectMapper) {
      this.resultsDirectory = Paths.get(fileStoreConfig.resultsDirectory);
      this.attributesDirectory = Paths.get(fileStoreConfig.attributesDirectory);
      this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
      this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {

      Path attributeLookupFile = attributesDirectory.resolve("tfb_lookup.json");

      if (!Files.isRegularFile(attributeLookupFile)) {
        exchange.setStatusCode(SERVICE_UNAVAILABLE);
        return;
      }

      String zipFileName = queryParameter(exchange, "zipFile");
      if (zipFileName == null) {
        exchange.setStatusCode(BAD_REQUEST);
        return;
      }

      boolean jsonFormat = "json".equals(queryParameter(exchange, "format"));

      Path requestedFile;
      try {
        requestedFile = resultsDirectory.resolve(zipFileName);
      } catch (InvalidPathException ignored) {
        exchange.setStatusCode(NOT_FOUND);
        return;
      }

      if (!requestedFile.equals(requestedFile.normalize())
          || !requestedFile.startsWith(resultsDirectory)) {
        exchange.setStatusCode(NOT_FOUND);
        return;
      }

      if (!Files.isRegularFile(requestedFile)
          || !MoreFiles.getFileExtension(requestedFile).equals("zip")) {
        exchange.setStatusCode(NOT_FOUND);
        return;
      }

      AttributeLookup attributeLookup;
      try {
        attributeLookup = objectMapper.readValue(attributeLookupFile.toFile(),
                                                 AttributeLookup.class);
      } catch (IOException e) {
        logger.warn("Exception thrown while reading tfb_lookup.json", e);
        exchange.setStatusCode(BAD_REQUEST);
        return;
      }

      ImmutableList<TestDefinition> newTests =
          ZipFiles.readZipEntry(
              /* zipFile= */ requestedFile,
              /* entryPath= */ "test_metadata.json",
              /* entryReader= */
              in -> objectMapper.readValue(
                  in,
                  new TypeReference<ImmutableList<TestDefinition>>() {}));

      if (newTests == null || newTests.isEmpty()) {
        exchange.setStatusCode(NOT_FOUND);
        return;
      }

      // We convert the minified test definitions from tfb_lookup.js to full
      // test definitions, since this will make it easier to compare them to the
      // new test definitions.

      ImmutableMap<Integer, TestDefinition> oldTestMetadata =
          mapToComplete(attributeLookup);

      ImmutableMap<Attribute, AttributeInfo> updatedAttributes =
          updateAttributes(attributeLookup.attributes, newTests);

      ImmutableMap<String, MinifiedTestDefinition> updatedTestMetadata =
          minifyTestMetadata(updatedAttributes, newTests, oldTestMetadata);

      if (jsonFormat) {

        AttributesJsonView jsonView =
            new AttributesJsonView(
                /* attributes= */ updatedAttributes,
                /* tests= */ updatedTestMetadata);

        String json = objectMapper.writeValueAsString(jsonView);
        exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_UTF_8.toString());
        exchange.getResponseSender().send(json, UTF_8);

      } else {

        String attributes;
        String tests;
        try {
          attributes = objectMapper.writeValueAsString(updatedAttributes);
          tests = objectMapper.writeValueAsString(updatedTestMetadata);
        } catch (IOException e) {
          logger.warn("Error thrown while mapping attributes to string", e);
          exchange.setStatusCode(BAD_REQUEST);
          return;
        }

        AttributesPageView pageView =
            new AttributesPageView(/* attributes= */ attributes,
                /* tests= */ tests,
                /* fileName= */ requestedFile.getFileName().toString());

        String html = mustacheRenderer.render("attributes.mustache", pageView);
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
    private ImmutableMap<Attribute, AttributeInfo>
    updateAttributes(Map<Attribute, AttributeInfo> previousAttributes,
                     List<TestDefinition> testDefinitions) {

      ImmutableMap.Builder<Attribute, AttributeInfo> updatedAttributes = new ImmutableMap.Builder<>();

      ImmutableList<Map<Attribute, String>> attributeMaps =
          testDefinitions.stream()
                         .map(definition -> testDefinitionToMap(definition))
                         .collect(toImmutableList());

      previousAttributes.forEach((attribute, info) -> {

        ImmutableSet<String> allValues =
            attributeMaps.stream()
                         .map(testDefinition -> testDefinition.get(attribute))
                         .collect(toImmutableSet());

        ImmutableSet<String> oldValuesLower =
            info.list.stream()
                     .map(Ascii::toLowerCase)
                     .collect(toImmutableSet());

        ImmutableSet<String> newValues =
            allValues.stream()
                     .filter(value -> !oldValuesLower.contains(Ascii.toLowerCase(value)))
                     .collect(toImmutableSet());

        ImmutableSet<String> newValuesLower =
            allValues.stream()
                     .map(Ascii::toLowerCase)
                     .collect(toImmutableSet());

        ImmutableSet<String> unusedValues =
            info.list.stream()
                     .filter(value -> !newValuesLower.contains(Ascii.toLowerCase(value)))
                     .collect(toImmutableSet());

        ImmutableList<String> updatedList =
            Stream.concat(info.list.stream(), newValues.stream())
                  .map(value -> {
                    if (!unusedValues.contains(value) || value.startsWith(UNUSED_MARKER)) {
                      return value;
                    } else {
                      return UNUSED_MARKER + value;
                    }
                  })
                  .collect(toImmutableList());

        updatedAttributes.put(
            attribute,
            new AttributeInfo(/* code= */ attribute.code(),
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
    private ImmutableMap<String, MinifiedTestDefinition>
    minifyTestMetadata(Map<Attribute, AttributeInfo> updatedAttributes,
                       List<TestDefinition> newTestDefinitions,
                       Map<Integer, TestDefinition> oldTestDefinitions) {

      int nextIdentity = 1 + oldTestDefinitions.keySet()
                                               .stream()
                                               .mapToInt(identity -> identity)
                                               .max()
                                               .orElse(0);

      Map<Integer, TestDefinition> testMetadata = new HashMap<>();

      for (TestDefinition newTest : newTestDefinitions) {
        Integer identity =
            oldTestDefinitions.entrySet()
                              .stream()
                              .filter(entry -> matchesOnAttributes(newTest, entry.getValue()))
                              .map(Map.Entry::getKey)
                              .findFirst()
                              .orElse(null);

        // In some cases we might have multiple new tests that match to on of the
        // old tests; in these cases we simply assign them a new identity to
        // avoid overriding.
        if (identity == null || testMetadata.containsKey(identity)) {
          identity = nextIdentity++;
        }

        testMetadata.put(identity, newTest);
      }

      Map<Attribute, List<String>> attributeToValuesLower = new EnumMap<>(Attribute.class);

      updatedAttributes.forEach(
          (attribute, info) ->
              attributeToValuesLower.put(
                  attribute,
                  info.list.stream()
                           .map(Ascii::toLowerCase)
                           .collect(toImmutableList())));

      ImmutableMap.Builder<String, MinifiedTestDefinition> result = new ImmutableMap.Builder<>();

      testMetadata.forEach((identity, definition) -> {

        String versusName = definition.versus;
        Integer versusIdentifier = null;

        if (versusName != null && !versusName.isEmpty()) {
          versusIdentifier =
              testMetadata.entrySet()
                          .stream()
                          .filter(entry -> {
                            TestDefinition other = entry.getValue();
                            return versusName.equalsIgnoreCase(other.framework)
                                || versusName.equalsIgnoreCase(other.name);
                          })
                          .map(Map.Entry::getKey)
                          .findFirst()
                          .orElse(null);
        }

        Map<Attribute, String> indexes = new EnumMap<>(Attribute.class);

        testDefinitionToMap(definition).forEach((attribute, value) -> {
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
                /* webserver= */
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

    private static boolean matchesOnAttributes(TestDefinition a, TestDefinition b) {
      return (a.name.equalsIgnoreCase(b.name))
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
     * @param attributeLookup the attributes and minified tests
     * @return a map of each test definition to its identity
     */
    private ImmutableMap<Integer, TestDefinition>
    mapToComplete(AttributeLookup attributeLookup) {

      ValuesByAttributeAndIndex values = new ValuesByAttributeAndIndex(attributeLookup);

      ImmutableMap.Builder<Integer, TestDefinition> result = new ImmutableMap.Builder<>();

      attributeLookup.minifiedTests.forEach((identity, minifiedTest) -> {

        String versus;
        if (minifiedTest.v.isEmpty()) {
          versus = "";
        } else {
          MinifiedTestDefinition versusTest =
              attributeLookup.minifiedTests.get(
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

      ValuesByAttributeAndIndex(AttributeLookup attributeLookup) {
        attributeLookup.attributes.forEach((attribute, info) -> {
          for (int index = 0; index < info.list.size(); index++) {
            table.put(attribute,
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
}
