package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import io.mockhub.template.WeightedTransformer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

  private InstantStubRules() {}

  public static final class Refused extends RuntimeException {
    Refused(String message) {
      super(message, null, false, false);
    }
  }

  public static ObjectNode check(JsonNode input) {
    if (input == null || !input.isObject()) throw new Refused("Send a stub as a JSON object with request and response");
    if (Jsons.write(input).getBytes(StandardCharsets.UTF_8).length > MAX_STUB_BYTES) throw new Refused("Keep each stub under 64 KB");
    ObjectNode stub = input.deepCopy();
    only(stub, TOP, "");
    stub.remove("id");
    stub.remove("uuid");
    stub.remove("persistent");
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
        if (FORBIDDEN_MATCHERS.contains(name)) {
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

  private static void transformers(ObjectNode response) {
    JsonNode list = response.get("transformers");
    if (list == null) return;
    if (!list.isArray()) throw new Refused("response.transformers must be a list");
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
