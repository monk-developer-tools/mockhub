package io.mockhub.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class PostmanImporter implements StubImporter {
  static final int MAX_STUBS = 1000;
  private static final Set<String> SKIPPED_HEADERS = Set.of("content-length", "transfer-encoding", "connection", "date", "keep-alive");
  private static final Pattern VARIABLE = Pattern.compile("\\{\\{[^}]*}}");
  private static final Pattern HOST = Pattern.compile("^(?:\\{\\{[^}]*}}|[a-zA-Z][a-zA-Z0-9+.-]*://[^/?#]*)");

  @Override
  public String format() {
    return "postman";
  }

  private record Context(List<ObjectNode> stubs, List<String> skipped, Set<String> seen, String stripPrefix, int[] requests, int[] examples) {}

  @Override
  public Result parse(Request request) {
    JsonNode root;
    try {
      root = Jsons.MAPPER.readTree(request.body());
    } catch (Exception e) {
      throw ApiException.badRequest("That isn't valid JSON. Export the collection from Postman as Collection v2.1");
    }
    if (root == null || !root.isObject()) throw ApiException.badRequest("That isn't a Postman collection");
    if (root.has("requests") && !root.has("item")) {
      throw ApiException.badRequest("This is a Postman v1 collection. Export it again as Collection v2.1");
    }
    if (!root.path("item").isArray()) throw ApiException.badRequest("That isn't a Postman collection: it has no item list");
    String strip = request.option("stripPrefix");
    Context ctx = new Context(new ArrayList<>(), new ArrayList<>(), new HashSet<>(), strip == null ? "" : normalPrefix(strip), new int[1], new int[1]);
    walk(root.get("item"), "", ctx);
    if (ctx.stubs().isEmpty()) throw ApiException.badRequest("The collection has no requests to turn into stubs");
    ObjectNode details = Jsons.object()
        .put("collection", root.path("info").path("name").asText("Postman collection"))
        .put("requests", ctx.requests()[0])
        .put("examples", ctx.examples()[0]);
    ArrayNode skipped = details.putArray("skipped");
    ctx.skipped().forEach(skipped::add);
    return new Result(List.copyOf(ctx.stubs()), details);
  }

  private void walk(JsonNode items, String folder, Context ctx) {
    for (JsonNode item : items) {
      if (item.path("item").isArray()) {
        String name = item.path("name").asText("Folder");
        walk(item.get("item"), folder.isEmpty() ? name : folder + " / " + name, ctx);
      } else if (item.has("request")) {
        request(item, folder, ctx);
      }
    }
  }

  private void request(JsonNode item, String folder, Context ctx) {
    ctx.requests()[0]++;
    String name = item.path("name").asText("Request");
    JsonNode request = item.get("request");
    JsonNode examples = item.path("response");
    if (!examples.isArray() || examples.isEmpty()) {
      add(ctx, stub(name, folder, request, request, null, ctx), name + ": no saved example response, so it answers 200 with an empty body");
      return;
    }
    for (JsonNode example : examples) {
      ctx.examples()[0]++;
      JsonNode original = example.has("originalRequest") && !example.get("originalRequest").isNull() ? example.get("originalRequest") : request;
      String label = examples.size() > 1 ? name + " · " + example.path("name").asText("Example") : name;
      add(ctx, stub(label, folder, original, request, example, ctx), null);
    }
  }

  private void add(Context ctx, ObjectNode stub, String note) {
    if (stub == null) return;
    String signature = Jsons.write(stub.get("request"));
    if (!ctx.seen().add(signature)) {
      ctx.skipped().add(stub.path("name").asText() + ": answers the same request as an earlier example, so it was left out");
      return;
    }
    if (ctx.stubs().size() >= MAX_STUBS) {
      ctx.skipped().add(stub.path("name").asText() + ": a collection imports at most " + MAX_STUBS + " stubs");
      return;
    }
    ctx.stubs().add(stub);
    if (note != null) ctx.skipped().add(note);
  }

  private ObjectNode stub(String name, String folder, JsonNode request, JsonNode described, JsonNode example, Context ctx) {
    JsonNode req = request.isTextual() ? Jsons.object().put("url", request.asText()) : request;
    String method = req.path("method").asText("GET").toUpperCase(Locale.ROOT);
    String path = path(req.get("url"), ctx.stripPrefix());
    if (path == null) {
      ctx.skipped().add(name + ": its URL is outside the stripped prefix " + ctx.stripPrefix());
      return null;
    }
    ObjectNode stub = Jsons.object().put("name", name);
    ObjectNode match = stub.putObject("request").put("method", method);
    if (path.contains("{")) match.put("urlPathTemplate", path);
    else match.put("urlPath", path);
    ObjectNode query = query(req.get("url"));
    if (!query.isEmpty()) match.set("queryParameters", query);
    JsonNode body = jsonBody(req.path("body"));
    if (body != null && example != null && !"GET".equals(method)) {
      match.putArray("bodyPatterns").addObject().put("equalToJson", body.toString()).put("ignoreExtraElements", true);
    }
    stub.put("priority", query.isEmpty() && body == null ? 10 : 5);
    ObjectNode response = stub.putObject("response").put("status", example == null ? 200 : example.path("code").asInt(200));
    if (example != null) {
      ObjectNode headers = Jsons.object();
      for (JsonNode h : example.path("header")) {
        String key = h.path("key").asText("");
        if (key.isEmpty() || h.path("disabled").asBoolean(false) || SKIPPED_HEADERS.contains(key.toLowerCase(Locale.ROOT))) continue;
        headers.put(key, h.path("value").asText(""));
      }
      if (!headers.isEmpty()) response.set("headers", headers);
      if (example.hasNonNull("body") && !example.get("body").asText().isEmpty()) response.put("body", example.get("body").asText());
    }
    ObjectNode meta = stub.putObject("metadata").putObject("mockhub");
    if (!folder.isEmpty()) meta.put("folder", folder);
    String description = description(described.isObject() ? described : Jsons.object());
    if (!description.isEmpty()) meta.put("description", description);
    return stub;
  }

  private static String description(JsonNode request) {
    JsonNode d = request.path("description");
    if (d.isTextual()) return cut(d.asText());
    return d.path("content").isTextual() ? cut(d.path("content").asText()) : "";
  }

  private static String cut(String text) {
    return text.length() > 500 ? text.substring(0, 500) : text;
  }

  static String path(JsonNode url, String stripPrefix) {
    List<String> segments = new ArrayList<>();
    if (url != null && url.isObject() && url.path("path").isArray()) {
      for (JsonNode p : url.get("path")) segments.add(p.isTextual() ? p.asText() : p.path("value").asText(""));
    } else {
      String raw = url == null ? "" : url.isTextual() ? url.asText() : url.path("raw").asText("");
      String rest = HOST.matcher(raw.trim()).replaceFirst("");
      int cut = indexOfAny(rest, "?#");
      if (cut >= 0) rest = rest.substring(0, cut);
      for (String s : rest.split("/")) segments.add(s);
    }
    StringBuilder out = new StringBuilder();
    for (String segment : segments) {
      if (segment == null || segment.isEmpty()) continue;
      out.append('/').append(segment(segment));
    }
    String path = out.isEmpty() ? "/" : out.toString();
    if (stripPrefix.isEmpty()) return path;
    if (path.equals(stripPrefix)) return "/";
    return path.startsWith(stripPrefix + "/") ? path.substring(stripPrefix.length()) : null;
  }

  private static String segment(String segment) {
    if (segment.startsWith(":") && segment.length() > 1) return "{" + safe(segment.substring(1)) + "}";
    if (segment.startsWith("{{") && segment.endsWith("}}") && segment.length() > 4) return "{" + safe(segment.substring(2, segment.length() - 2)) + "}";
    return segment;
  }

  private static String safe(String name) {
    String clean = name.trim().replaceAll("[^A-Za-z0-9_]", "_");
    return clean.isEmpty() ? "param" : clean;
  }

  private static int indexOfAny(String text, String chars) {
    int best = -1;
    for (char c : chars.toCharArray()) {
      int i = text.indexOf(c);
      if (i >= 0 && (best < 0 || i < best)) best = i;
    }
    return best;
  }

  private static ObjectNode query(JsonNode url) {
    ObjectNode out = Jsons.object();
    if (url == null || !url.isObject()) return out;
    for (JsonNode q : url.path("query")) {
      String key = q.path("key").asText("");
      String value = q.path("value").asText("");
      if (key.isEmpty() || q.path("disabled").asBoolean(false) || VARIABLE.matcher(value).find() || VARIABLE.matcher(key).find()) continue;
      out.putObject(key).put("equalTo", value);
    }
    return out;
  }

  private static JsonNode jsonBody(JsonNode body) {
    if (!"raw".equals(body.path("mode").asText()) || !body.path("raw").isTextual()) return null;
    String raw = body.get("raw").asText().trim();
    if (raw.isEmpty() || VARIABLE.matcher(raw).find() || !(raw.startsWith("{") || raw.startsWith("["))) return null;
    try {
      return Jsons.MAPPER.readTree(raw);
    } catch (Exception e) {
      return null;
    }
  }

  private static String normalPrefix(String prefix) {
    String p = prefix.trim();
    if (!p.startsWith("/")) p = "/" + p;
    return p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
  }
}
