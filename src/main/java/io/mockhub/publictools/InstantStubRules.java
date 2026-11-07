package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import io.mockhub.data.CrudResource;
import io.mockhub.data.CrudStubs;
import io.mockhub.graphql.GraphQlException;
import io.mockhub.graphql.GraphQlTransformer;
import io.mockhub.graphql.SchemaSource;
import io.mockhub.template.WeightedTransformer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class InstantStubRules {
  public static final int MAX_STUB_BYTES = 64 * 1024;
  public static final int MAX_DELAY_MS = 10_000;
  public static final String TEMPLATE = "response-template";

  private static final Set<String> TOP = Set.of("id", "uuid", "name", "request", "response", "priority", "scenarioName",
      "requiredScenarioState", "newScenarioState", "persistent");
  private static final Set<String> REQUEST = Set.of("method", "url", "urlPath", "urlPathTemplate", "queryParameters", "headers",
      "cookies", "bodyPatterns", "pathParameters", "formParameters", "basicAuthCredentials");
  private static final Set<String> RESPONSE = Set.of("status", "statusMessage", "headers", "body", "jsonBody", "base64Body",
      "fixedDelayMilliseconds", "delayDistribution", "chunkedDribbleDelay", "fault", "transformers", "transformerParameters");
  private static final Set<String> FORBIDDEN_MATCHERS = Set.of("matches", "doesNotMatch", "matchesXPath", "equalToXml",
      "matchesJsonSchema", "urlPattern", "urlPathPattern", "customMatcher", "multipartPatterns");
  private static final Set<String> TRANSFORMERS = Set.of(TEMPLATE, WeightedTransformer.NAME);
  private static final Pattern SOAP_BODY = Pattern.compile("//\\*\\[local-name\\(\\)='Body'\\]/\\*\\[local-name\\(\\)='[A-Za-z_][A-Za-z0-9_.-]{0,100}'\\]");

  private InstantStubRules() {}

  public static final class Refused extends RuntimeException {
    Refused(String message) {
      super(message, null, false, false);
    }
  }

  public static ObjectNode check(JsonNode input) {
    if (input == null || !input.isObject()) throw new Refused("Send a stub as a JSON object with request and response");
    ObjectNode stub = input.deepCopy();
    size(stub);
    only(stub, TOP, "");
    stub.remove("id");
    stub.remove("uuid");
    stub.remove("persistent");
    if (isCrud(stub)) return crud(stub);
    if (!stub.path("request").isObject()) throw new Refused("The stub needs a request");
    if (!stub.path("response").isObject()) throw new Refused("The stub needs a response");
    ObjectNode request = (ObjectNode) stub.get("request");
    only(request, REQUEST, "request.");
    matchers(request, "request");
    ObjectNode response = (ObjectNode) stub.get("response");
    only(response, RESPONSE, "response.");
    delays(response);
    transformers(response);
    return stub;
  }

  public static ObjectNode checkImported(JsonNode input) {
    if (input == null || !input.isObject() || !input.has("metadata")) return check(input);
    ObjectNode copy = input.deepCopy();
    copy.remove("metadata");
    return check(copy);
  }

  public static ObjectNode forDisplay(JsonNode stored) {
    ObjectNode stub = stored.deepCopy();
    stub.remove(List.of("metadata", "persistent", "uuid", "insertionIndex"));
    JsonNode list = stub.path("response").path("transformers");
    if (list.isArray()) {
      ArrayNode shown = Jsons.MAPPER.createArrayNode();
      list.forEach(t -> shown.add(t.asText().equals(SandboxTemplateTransformer.NAME) ? TEMPLATE : t.asText()));
      ((ObjectNode) stub.get("response")).set("transformers", shown);
    }
    return stub;
  }

  private static void only(ObjectNode node, Set<String> allowed, String prefix) {
    Iterator<String> names = node.fieldNames();
    while (names.hasNext()) {
      String name = names.next();
      if (!allowed.contains(name)) throw new Refused(prefix + name + " isn't available in instant mocks. Run MockHub yourself to use it.");
    }
  }

  private static void matchers(JsonNode node, String path) {
    if (node.isObject()) {
      for (Map.Entry<String, JsonNode> field : node.properties()) {
        String name = field.getKey();
        if (FORBIDDEN_MATCHERS.contains(name) && !safe(name, field.getValue())) {
          throw new Refused(name + " (at " + path + ") isn't available in instant mocks: regex, XPath and schema matching are turned off on the public demo");
        }
        if (name.equals("matchesJsonPath") && field.getValue().toString().contains("=~")) {
          throw new Refused("Regex filters (=~) in matchesJsonPath aren't available in instant mocks");
        }
        matchers(field.getValue(), path + "." + name);
      }
    } else if (node.isArray()) {
      for (JsonNode item : node) matchers(item, path);
    }
  }

  private static boolean safe(String matcher, JsonNode value) {
    if (!value.isTextual()) return false;
    if (matcher.equals("matches")) return value.asText().equals(".*");
    return matcher.equals("matchesXPath") && SOAP_BODY.matcher(value.asText()).matches();
  }

  private static boolean isCrud(ObjectNode stub) {
    JsonNode list = stub.path("response").path("transformers");
    if (!list.isArray()) return false;
    for (JsonNode t : list) {
      if (t.asText().equals(CrudStubs.TRANSFORMER)) return true;
    }
    return false;
  }

  private static ObjectNode crud(ObjectNode stub) {
    JsonNode response = stub.get("response");
    if (response.get("transformers").size() != 1) throw new Refused("A CRUD stub can't use other transformers");
    JsonNode parameters = response.path("transformerParameters");
    CrudResource resource;
    try {
      resource = new CrudResource(parameters.path("resource").asText(), parameters.path("idField").asText("id"),
          CrudResource.IdType.parse(parameters.path("idType").asText("number")));
    } catch (RuntimeException e) {
      throw new Refused("That CRUD resource isn't valid: " + e.getMessage());
    }
    ObjectNode canonical = CrudStubs.stub(resource);
    if (stub.hasNonNull("name")) canonical.put("name", stub.get("name").asText());
    if (stub.hasNonNull("priority")) canonical.set("priority", stub.get("priority"));
    return canonical;
  }

  private static void delays(ObjectNode response) {
    if (response.path("fixedDelayMilliseconds").asLong(0) > MAX_DELAY_MS) throw new Refused("Delays go up to 10,000 ms in instant mocks");
    JsonNode distribution = response.get("delayDistribution");
    if (distribution != null) {
      if (!"uniform".equals(distribution.path("type").asText())) throw new Refused("Only uniform delay distributions are available in instant mocks");
      if (distribution.path("upper").asLong(0) > MAX_DELAY_MS) throw new Refused("Delays go up to 10,000 ms in instant mocks");
    }
    if (response.path("chunkedDribbleDelay").path("totalDuration").asLong(0) > MAX_DELAY_MS) {
      throw new Refused("Delays go up to 10,000 ms in instant mocks");
    }
  }

  private static void size(ObjectNode stub) {
    JsonNode schema = stub.path("response").path("transformerParameters").path(GraphQlTransformer.SCHEMA);
    ObjectNode rest = stub;
    if (schema.isTextual()) {
      if (schema.asText().length() > InstantSources.MAX_SPEC) throw new Refused("Keep the GraphQL schema under 500 KB");
      rest = stub.deepCopy();
      ((ObjectNode) rest.path("response").path("transformerParameters")).remove(GraphQlTransformer.SCHEMA);
    }
    if (Jsons.write(rest).getBytes(StandardCharsets.UTF_8).length > MAX_STUB_BYTES) throw new Refused("Keep each stub under 64 KB");
  }

  private static boolean graphql(JsonNode list) {
    for (JsonNode t : list) {
      if (t.asText().equals(GraphQlTransformer.NAME)) return true;
    }
    return false;
  }

  private static void graphqlEndpoint(ObjectNode response) {
    if (response.get("transformers").size() != 1) throw new Refused("A GraphQL stub can't use other transformers");
    JsonNode parameters = response.path("transformerParameters");
    if (!parameters.path(GraphQlTransformer.SCHEMA).isTextual()) throw new Refused("A GraphQL stub needs its schema in transformerParameters.schema");
    try {
      ((ObjectNode) parameters).put(GraphQlTransformer.SCHEMA, SchemaSource.toSdl(parameters.get(GraphQlTransformer.SCHEMA).asText()));
    } catch (GraphQlException e) {
      throw new Refused(e.getMessage());
    }
  }

  private static void transformers(ObjectNode response) {
    JsonNode list = response.get("transformers");
    if (list == null) return;
    if (!list.isArray()) throw new Refused("response.transformers must be a list");
    if (graphql(list)) {
      graphqlEndpoint(response);
      return;
    }
    ArrayNode ordered = Jsons.MAPPER.createArrayNode();
    boolean template = false;
    for (JsonNode t : list) {
      String name = t.asText();
      if (!TRANSFORMERS.contains(name)) throw new Refused("The " + name + " transformer isn't available in instant mocks");
      if (name.equals(TEMPLATE)) template = true;
      else ordered.add(name);
    }
    if (template) ordered.add(SandboxTemplateTransformer.NAME);
    response.set("transformers", ordered);
  }
}
