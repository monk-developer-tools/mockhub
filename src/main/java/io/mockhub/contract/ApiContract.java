package io.mockhub.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApiContract {
  static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "patch", "head", "options", "trace", "query");
  private static final Set<String> SWAGGER_SCHEMA_FIELDS = Set.of("type", "format", "items", "enum", "minimum", "maximum",
      "exclusiveMinimum", "exclusiveMaximum", "pattern", "minLength", "maxLength", "minItems", "maxItems", "uniqueItems", "multipleOf");
  private static final Pattern VARIABLE = Pattern.compile("\\{([^}/]+)}");

  public record Param(String name, String in, boolean required, JsonNode schema) {}

  public record Body(boolean required, Map<String, JsonNode> media) {}

  public record Operation(String method, String path, Pattern regex, int literals, List<String> pathNames, List<Param> params,
                          Body requestBody, Map<String, Map<String, JsonNode>> responses) {}

  public record Match(Operation operation, boolean pathKnown, String template, Set<String> methods, Map<String, String> pathValues) {}

  private final JsonNode spec;
  private final List<Operation> operations;
  private final List<String> prefixes;
  private final Set<String> securityHeaders;
  private final Set<String> securityQuery;
  private final SchemaChecker schemas;
  private final String strippedPrefix;

  private ApiContract(JsonNode spec, String strippedPrefix) {
    this.spec = spec;
    this.strippedPrefix = strippedPrefix == null || strippedPrefix.isBlank() || strippedPrefix.equals("/") ? null : trimSlash(strippedPrefix.trim());
    boolean swagger2 = spec.has("swagger");
    this.operations = readOperations(swagger2);
    this.prefixes = readPrefixes(swagger2);
    this.securityHeaders = securityNames(swagger2, "header");
    this.securityQuery = securityNames(swagger2, "query");
    this.schemas = new SchemaChecker(spec);
  }

  public static ApiContract of(JsonNode spec) {
    return new ApiContract(spec, null);
  }

  public static ApiContract of(JsonNode spec, String strippedPrefix) {
    return new ApiContract(spec, strippedPrefix);
  }

  public List<Operation> operations() {
    return operations;
  }

  public List<String> prefixes() {
    return prefixes;
  }

  boolean documentsHeader(String lowerName) {
    return securityHeaders.contains(lowerName);
  }

  boolean documentsQuery(String name) {
    return securityQuery.contains(name);
  }

  List<String> validate(JsonNode schema, JsonNode value) {
    return schemas.check(schema, value);
  }

  public Match match(String method, String rawPath) {
    String path = rawPath.length() > 1 && rawPath.endsWith("/") ? rawPath.substring(0, rawPath.length() - 1) : rawPath;
    List<String> candidates = new ArrayList<>();
    candidates.add(path);
    if (strippedPrefix != null) candidates.add(path.equals("/") ? strippedPrefix : strippedPrefix + path);
    for (String prefix : prefixes) {
      if (path.equals(prefix)) candidates.add("/");
      else if (path.startsWith(prefix + "/")) candidates.add(path.substring(prefix.length()));
    }
    for (String candidate : candidates) {
      Operation best = null;
      Matcher bestMatcher = null;
      for (Operation op : operations) {
        Matcher m = op.regex().matcher(candidate);
        if (!m.matches()) continue;
        if (best == null || op.literals() > best.literals()) {
          best = op;
          bestMatcher = m;
        }
      }
      if (best == null) continue;
      String template = best.path();
      Set<String> methods = new TreeSet<>();
      Operation chosen = null;
      for (Operation op : operations) {
        if (!op.path().equals(template)) continue;
        methods.add(op.method());
        if (op.method().equals(method)) chosen = op;
      }
      Map<String, String> values = new LinkedHashMap<>();
      for (int i = 0; i < best.pathNames().size(); i++) values.put(best.pathNames().get(i), decode(bestMatcher.group(i + 1)));
      return new Match(chosen, true, template, methods, values);
    }
    return new Match(null, false, null, Set.of(), Map.of());
  }

  private List<Operation> readOperations(boolean swagger2) {
    List<Operation> out = new ArrayList<>();
    for (Iterator<Map.Entry<String, JsonNode>> paths = spec.path("paths").fields(); paths.hasNext(); ) {
      Map.Entry<String, JsonNode> pathEntry = paths.next();
      String template = pathEntry.getKey();
      JsonNode item = deref(pathEntry.getValue());
      List<String> names = new ArrayList<>();
      Pattern regex = compile(template, names);
      int literals = (int) java.util.Arrays.stream(template.split("/")).filter(s -> !s.isEmpty() && !s.contains("{")).count();
      for (Iterator<Map.Entry<String, JsonNode>> ops = item.fields(); ops.hasNext(); ) {
        Map.Entry<String, JsonNode> opEntry = ops.next();
        if (!METHODS.contains(opEntry.getKey())) continue;
        JsonNode op = opEntry.getValue();
        Map<String, JsonNode> rawParams = new LinkedHashMap<>();
        for (JsonNode p : item.path("parameters")) addParam(rawParams, p);
        for (JsonNode p : op.path("parameters")) addParam(rawParams, p);
        List<Param> params = new ArrayList<>();
        Body body = null;
        List<String> consumes = media(op, "consumes");
        for (JsonNode p : rawParams.values()) {
          String in = p.path("in").asText();
          if (swagger2 && in.equals("body")) {
            Map<String, JsonNode> media = new LinkedHashMap<>();
            for (String type : consumes) media.put(type, p.get("schema"));
            body = new Body(p.path("required").asBoolean(false), media);
          } else if (swagger2 && in.equals("formData")) {
            Map<String, JsonNode> media = new LinkedHashMap<>();
            for (String type : consumes.isEmpty() || consumes.equals(List.of("application/json"))
                ? List.of("application/x-www-form-urlencoded", "multipart/form-data") : consumes) media.put(type, null);
            if (body == null) body = new Body(false, media);
          } else {
            JsonNode schema = swagger2 ? swaggerSchema(p) : p.get("schema");
            params.add(new Param(p.path("name").asText(), in, p.path("required").asBoolean(in.equals("path")), schema));
          }
        }
        if (!swagger2 && op.has("requestBody")) {
          JsonNode rb = deref(op.get("requestBody"));
          body = new Body(rb.path("required").asBoolean(false), contentOf(rb.path("content")));
        }
        Map<String, Map<String, JsonNode>> responses = new LinkedHashMap<>();
        List<String> produces = media(op, "produces");
        for (Iterator<Map.Entry<String, JsonNode>> rs = op.path("responses").fields(); rs.hasNext(); ) {
          Map.Entry<String, JsonNode> r = rs.next();
          JsonNode response = deref(r.getValue());
          Map<String, JsonNode> media = new LinkedHashMap<>();
          if (swagger2) {
            if (response.has("schema")) for (String type : produces) media.put(type, response.get("schema"));
          } else {
            media = contentOf(response.path("content"));
          }
          responses.put(r.getKey().toUpperCase(Locale.ROOT), media);
        }
        out.add(new Operation(opEntry.getKey().toUpperCase(Locale.ROOT), template, regex, literals, List.copyOf(names), List.copyOf(params),
            body, responses));
      }
    }
    out.sort(Comparator.comparingInt(Operation::literals).reversed());
    return List.copyOf(out);
  }

  private void addParam(Map<String, JsonNode> params, JsonNode raw) {
    JsonNode p = deref(raw);
    params.put(p.path("in").asText() + ":" + p.path("name").asText(), p);
  }

  private Map<String, JsonNode> contentOf(JsonNode content) {
    Map<String, JsonNode> media = new LinkedHashMap<>();
    content.fields().forEachRemaining(e -> media.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().get("schema")));
    return media;
  }

  private List<String> media(JsonNode op, String field) {
    JsonNode list = op.has(field) ? op.get(field) : spec.get(field);
    List<String> out = new ArrayList<>();
    if (list != null) list.forEach(n -> out.add(n.asText().toLowerCase(Locale.ROOT)));
    if (out.isEmpty()) out.add("application/json");
    return out;
  }

  private static JsonNode swaggerSchema(JsonNode param) {
    ObjectNode schema = Jsons.object();
    param.fields().forEachRemaining(e -> {
      if (SWAGGER_SCHEMA_FIELDS.contains(e.getKey())) schema.set(e.getKey(), e.getValue());
    });
    return schema.isEmpty() ? null : schema;
  }

  private JsonNode deref(JsonNode node) {
    int guard = 0;
    while (node != null && node.has("$ref") && guard++ < 10) {
      String ref = node.get("$ref").asText();
      if (!ref.startsWith("#/")) return Jsons.object();
      JsonNode cur = spec;
      for (String part : ref.substring(2).split("/")) cur = cur.path(part.replace("~1", "/").replace("~0", "~"));
      node = cur.isMissingNode() ? Jsons.object() : cur;
    }
    return node == null ? Jsons.object() : node;
  }

  private List<String> readPrefixes(boolean swagger2) {
    Set<String> out = new LinkedHashSet<>();
    if (swagger2) {
      String base = spec.path("basePath").asText("");
      if (base.length() > 1) out.add(trimSlash(base));
    } else {
      for (JsonNode server : spec.path("servers")) {
        String url = server.path("url").asText("");
        if (url.contains("{")) continue;
        try {
          String path = url.startsWith("/") ? url : URI.create(url).getPath();
          if (path != null && path.length() > 1) out.add(trimSlash(path));
        } catch (IllegalArgumentException ignored) {
          out.remove("");
        }
      }
    }
    return List.copyOf(out);
  }

  private Set<String> securityNames(boolean swagger2, String in) {
    JsonNode schemes = swagger2 ? spec.path("securityDefinitions") : spec.path("components").path("securitySchemes");
    Set<String> out = new LinkedHashSet<>();
    schemes.forEach(s -> {
      JsonNode scheme = deref(s);
      if (scheme.path("type").asText().equals("apiKey") && scheme.path("in").asText().equals(in)) {
        String name = scheme.path("name").asText();
        out.add(in.equals("header") ? name.toLowerCase(Locale.ROOT) : name);
      }
    });
    return out;
  }

  private static Pattern compile(String raw, List<String> names) {
    String template = raw.length() > 1 && raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    StringBuilder regex = new StringBuilder();
    Matcher m = VARIABLE.matcher(template);
    int last = 0;
    while (m.find()) {
      regex.append(Pattern.quote(template.substring(last, m.start())));
      regex.append("([^/]+)");
      names.add(m.group(1));
      last = m.end();
    }
    regex.append(Pattern.quote(template.substring(last)));
    return Pattern.compile(regex.toString());
  }

  private static String trimSlash(String path) {
    return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
  }

  private static String decode(String value) {
    try {
      return java.net.URLDecoder.decode(value.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return value;
    }
  }
}
