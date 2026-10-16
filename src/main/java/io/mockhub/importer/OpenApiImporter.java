package io.mockhub.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OpenApiImporter implements StubImporter {
  private static final long SEED = 20261016L;
  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
  private static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "patch", "head", "options");
  private static final int MAX_DEPTH = 8;

  @Override
  public String format() {
    return "openapi";
  }

  @Override
  public Result parse(Request request) {
    JsonNode spec = read(request.body());
    boolean swagger2 = spec.has("swagger");
    if (!swagger2 && !spec.has("openapi")) {
      throw ApiException.badRequest("Not an OpenAPI 3 or Swagger 2 document (missing 'openapi'/'swagger')");
    }
    String serverPrefix = request.option("stripPrefix") != null ? request.option("stripPrefix") : "";

    List<ObjectNode> stubs = new ArrayList<>();
    ArrayNode operations = Jsons.MAPPER.createArrayNode();
    for (Iterator<Map.Entry<String, JsonNode>> paths = spec.path("paths").fields(); paths.hasNext(); ) {
      var pathEntry = paths.next();
      String path = pathEntry.getKey();
      if (!serverPrefix.isEmpty() && path.startsWith(serverPrefix)) path = path.substring(serverPrefix.length());
      for (Iterator<Map.Entry<String, JsonNode>> ops = pathEntry.getValue().fields(); ops.hasNext(); ) {
        var opEntry = ops.next();
        if (!METHODS.contains(opEntry.getKey())) continue;
        JsonNode op = opEntry.getValue();
        stubs.add(stub(spec, swagger2, opEntry.getKey().toUpperCase(), path, op));
        operations.addObject().put("method", opEntry.getKey().toUpperCase()).put("path", path)
            .put("operationId", op.path("operationId").asText(""));
      }
    }
    ObjectNode details = Jsons.object();
    details.put("title", spec.path("info").path("title").asText(""));
    details.put("version", spec.path("info").path("version").asText(""));
    details.set("operations", operations);
    return new Result(stubs, details);
  }

  private ObjectNode stub(JsonNode spec, boolean swagger2, String method, String path, JsonNode op) {
    ObjectNode stub = Jsons.object();
    String summary = op.path("summary").asText(op.path("operationId").asText(method + " " + path));
    stub.put("name", summary);
    ObjectNode request = stub.putObject("request");
    request.put("method", method);
    if (path.contains("{")) request.put("urlPathTemplate", path);
    else request.put("urlPath", path);

    String status = "200";
    JsonNode response = null;
    for (Iterator<Map.Entry<String, JsonNode>> it = op.path("responses").fields(); it.hasNext(); ) {
      var e = it.next();
      if (e.getKey().startsWith("2")) {
        status = e.getKey();
        response = e.getValue();
        break;
      }
      if (response == null && e.getKey().equals("default")) response = e.getValue();
    }
    response = deref(spec, response);

    ObjectNode out = stub.putObject("response");
    out.put("status", parseStatus(status));
    JsonNode body = null;
    String contentType = "application/json";
    if (response != null) {
      if (swagger2) {
        body = firstExample(response.path("examples"));
        if (body == null && response.has("schema")) body = sample(spec, response.get("schema"), 0, new ArrayDeque<>());
      } else {
        JsonNode content = response.path("content");
        Iterator<String> types = content.fieldNames();
        if (types.hasNext()) {
          contentType = types.next();
          for (Iterator<String> t = content.fieldNames(); t.hasNext(); ) {
            String ct = t.next();
            if (ct.contains("json")) {
              contentType = ct;
              break;
            }
          }
          JsonNode media = content.get(contentType);
          if (media.has("example")) body = media.get("example");
          if (body == null) body = firstExample(media.path("examples"));
          if (body == null && media.has("schema")) body = sample(spec, media.get("schema"), 0, new ArrayDeque<>());
        }
      }
    }
    if (body != null) {
      out.putObject("headers").put("Content-Type", contentType);
      if (contentType.contains("json")) out.set("jsonBody", body);
      else out.put("body", body.isTextual() ? body.asText() : body.toString());
    }
    ObjectNode meta = stub.putObject("metadata").putObject("mockhub");
    meta.put("type", "rest");
    if (op.has("tags") && op.get("tags").isArray() && !op.get("tags").isEmpty()) meta.put("folder", op.get("tags").get(0).asText());
    if (op.has("description")) meta.put("description", op.get("description").asText());
    return stub;
  }

  JsonNode sample(JsonNode spec, JsonNode schema, int depth, Deque<String> refs) {
    return sample(spec, schema, depth, refs, "", new SampleValues(SEED + (schema == null ? 0 : schema.toString().hashCode())));
  }

  private JsonNode sample(JsonNode spec, JsonNode schema, int depth, Deque<String> refs, String name, SampleValues values) {
    if (schema == null || depth > MAX_DEPTH) return Jsons.MAPPER.nullNode();
    if (schema.has("$ref")) {
      String ref = schema.get("$ref").asText();
      if (refs.contains(ref)) return Jsons.MAPPER.nullNode();
      refs.push(ref);
      try {
        return sample(spec, resolveRef(spec, ref), depth + 1, refs, name, values);
      } finally {
        refs.pop();
      }
    }
    if (schema.has("example")) return schema.get("example");
    if (schema.has("default")) return schema.get("default");
    if (schema.has("enum") && schema.get("enum").isArray() && !schema.get("enum").isEmpty()) return schema.get("enum").get(0);
    for (String combiner : List.of("allOf", "oneOf", "anyOf")) {
      if (schema.has(combiner)) {
        if (combiner.equals("allOf")) {
          ObjectNode merged = Jsons.object();
          for (JsonNode part : schema.get(combiner)) {
            JsonNode s = sample(spec, part, depth + 1, refs, name, values);
            if (s.isObject()) merged.setAll((ObjectNode) s);
          }
          return merged;
        }
        return sample(spec, schema.get(combiner).get(0), depth + 1, refs, name, values);
      }
    }
    String type = schema.path("type").asText(schema.has("properties") ? "object" : schema.has("items") ? "array" : "string");
    switch (type) {
      case "object": {
        ObjectNode obj = Jsons.object();
        for (Iterator<Map.Entry<String, JsonNode>> it = schema.path("properties").fields(); it.hasNext(); ) {
          var p = it.next();
          obj.set(p.getKey(), sample(spec, p.getValue(), depth + 1, refs, p.getKey(), values));
        }
        return obj;
      }
      case "array": {
        ArrayNode arr = Jsons.MAPPER.createArrayNode();
        int items = depth == 0 ? SampleValues.ARRAY_ITEMS : 1;
        for (int i = 0; i < items; i++) arr.add(sample(spec, schema.path("items"), depth + 1, refs, singular(name), values));
        return arr;
      }
      case "integer":
        return Jsons.MAPPER.getNodeFactory().numberNode(values.integer(name));
      case "number":
        return Jsons.MAPPER.getNodeFactory().numberNode(values.number(name));
      case "boolean":
        return Jsons.MAPPER.getNodeFactory().booleanNode(values.bool());
      default:
        return Jsons.MAPPER.getNodeFactory().textNode(values.text(name, schema.path("format").asText("")));
    }
  }

  private static String singular(String name) {
    return name.endsWith("ies") ? name.substring(0, name.length() - 3) + "y" : name.endsWith("s") ? name.substring(0, name.length() - 1) : name;
  }

  private static JsonNode firstExample(JsonNode examples) {
    if (examples == null || !examples.isObject() || examples.isEmpty()) return null;
    JsonNode first = examples.elements().next();
    return first.has("value") ? first.get("value") : first;
  }

  private static JsonNode deref(JsonNode spec, JsonNode node) {
    int guard = 0;
    while (node != null && node.has("$ref") && guard++ < 10) node = resolveRef(spec, node.get("$ref").asText());
    return node;
  }

  private static JsonNode resolveRef(JsonNode spec, String ref) {
    if (!ref.startsWith("#/")) return Jsons.object();
    JsonNode cur = spec;
    for (String part : ref.substring(2).split("/")) {
      cur = cur.path(part.replace("~1", "/").replace("~0", "~"));
    }
    return cur.isMissingNode() ? Jsons.object() : cur;
  }

  private static int parseStatus(String status) {
    try {
      return Integer.parseInt(status);
    } catch (NumberFormatException e) {
      return 200;
    }
  }

  private static JsonNode read(String body) {
    if (body == null || body.isBlank()) throw ApiException.badRequest("Send the OpenAPI document as the request body");
    try {
      String trimmed = body.trim();
      return trimmed.startsWith("{") ? Jsons.MAPPER.readTree(trimmed) : YAML.readTree(trimmed);
    } catch (Exception e) {
      throw ApiException.badRequest("Cannot parse OpenAPI document: " + e.getMessage());
    }
  }
}
