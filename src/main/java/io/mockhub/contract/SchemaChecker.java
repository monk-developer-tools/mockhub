package io.mockhub.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.mockhub.api.Jsons;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SchemaChecker {
  private static final Set<String> ANNOTATIONS = Set.of("discriminator", "xml", "example", "externalDocs", "deprecated", "nullable");
  private static final int MAX_MESSAGES = 5;

  private final JsonSchemaFactory factory;
  private final SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().pathType(PathType.JSON_PATH).typeLoose(false).build();
  private final ObjectNode definitions = Jsons.object();
  private final boolean legacy;
  private final Map<JsonNode, JsonSchema> compiled = new IdentityHashMap<>();

  SchemaChecker(JsonNode spec) {
    legacy = !spec.path("openapi").asText("").startsWith("3.1");
    factory = JsonSchemaFactory.getInstance(legacy ? SpecVersion.VersionFlag.V4 : SpecVersion.VersionFlag.V202012);
    if (spec.has("components")) {
      ObjectNode components = definitions.putObject("components");
      components.set("schemas", convert(spec.path("components").path("schemas")));
    }
    if (spec.has("definitions")) definitions.set("definitions", convert(spec.path("definitions")));
  }

  List<String> check(JsonNode schema, JsonNode value) {
    JsonSchema compiledSchema;
    synchronized (compiled) {
      compiledSchema = compiled.computeIfAbsent(schema, this::compile);
    }
    List<String> out = new ArrayList<>();
    for (ValidationMessage m : compiledSchema.validate(value)) {
      if (out.size() >= MAX_MESSAGES) break;
      out.add(m.getMessage());
    }
    return out;
  }

  private JsonSchema compile(JsonNode schema) {
    ObjectNode root = definitions.deepCopy();
    root.putArray("allOf").add(convert(schema));
    return factory.getSchema(root, config);
  }

  private JsonNode convert(JsonNode node) {
    if (node == null || node.isMissingNode()) return Jsons.object();
    if (node.isArray()) {
      ArrayNode out = Jsons.MAPPER.createArrayNode();
      node.forEach(n -> out.add(convert(n)));
      return out;
    }
    if (!node.isObject()) return node.deepCopy();
    ObjectNode out = Jsons.object();
    for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
      Map.Entry<String, JsonNode> e = it.next();
      String key = e.getKey();
      if (key.startsWith("x-") || ANNOTATIONS.contains(key) || (legacy && key.equals("examples"))) continue;
      if (key.equals("enum") || key.equals("const") || key.equals("default")) {
        out.set(key, e.getValue().deepCopy());
      } else if (key.equals("properties") || key.equals("patternProperties") || key.equals("$defs")) {
        ObjectNode props = out.putObject(key);
        e.getValue().fields().forEachRemaining(p -> props.set(p.getKey(), convert(p.getValue())));
      } else {
        out.set(key, convert(e.getValue()));
      }
    }
    if (node.path("nullable").asBoolean(false)) allowNull(out);
    return out;
  }

  private static void allowNull(ObjectNode schema) {
    JsonNode type = schema.get("type");
    if (type != null && type.isTextual()) {
      schema.putArray("type").add(type.asText()).add("null");
    }
    JsonNode values = schema.get("enum");
    if (values != null && values.isArray()) ((ArrayNode) values).addNull();
    if (type == null && values == null && (schema.has("$ref") || schema.has("allOf") || schema.has("oneOf") || schema.has("anyOf"))) {
      ObjectNode inner = schema.deepCopy();
      schema.removeAll();
      schema.putArray("anyOf").add(inner).addObject().put("type", "null");
    }
  }
}
