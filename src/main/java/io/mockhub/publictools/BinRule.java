package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public record BinRule(String id, String name, String description, boolean enabled, String method, List<Condition> conditions, List<Answer> answers) {
  public static final int MAX_RULES = 50;
  public static final int MAX_CONDITIONS = 10;
  public static final int MAX_ANSWERS = 5;
  public static final int MAX_DELAY_MS = 15_000;
  public static final int MAX_BODY = 16 * 1024;
  public static final int MAX_HEADERS = 20;
  public static final int MAX_DESCRIPTION = 500;
  public static final int MAX_ANSWER_NOTE = 120;
  static final Set<String> METHODS = Set.of("ANY", "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "QUERY", "TRACE");

  public enum Target {
    PATH, QUERY, HEADER, BODY, JSON, FORM, XML, SOAP_ACTION, GRAPHQL;

    static Target of(String value) {
      try {
        return valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("A condition checks path, query, header, body, json, form, xml, soap_action or graphql, not " + value);
      }
    }

    boolean needsKey() {
      return this == QUERY || this == HEADER || this == JSON || this == FORM || this == XML;
    }
  }

  public enum Op {
    EQUALS, NOT_EQUALS, STARTS_WITH, CONTAINS, NOT_CONTAINS, MATCHES, TEMPLATE, EXISTS, ABSENT;

    static Op of(String value) {
      try {
        return valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("A condition uses equals, not_equals, starts_with, contains, not_contains, matches, template, exists or absent, not " + value);
      }
    }

    boolean needsValue() {
      return this != EXISTS && this != ABSENT;
    }
  }

  public record Condition(Target target, String key, Op op, String value) {}

  public record Answer(int weight, int status, int delayMs, Map<String, String> headers, String body, boolean template, String description, String fileName) {
    public boolean file() {
      return !fileName.isEmpty();
    }

    public byte[] bytes() {
      return file() ? Base64.getDecoder().decode(body) : body.getBytes(StandardCharsets.UTF_8);
    }
  }

  public static List<BinRule> listFrom(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) return List.of();
    if (!node.isArray()) throw new IllegalArgumentException("Send rules as a list");
    if (node.size() > MAX_RULES) throw new IllegalArgumentException("A bin holds up to " + MAX_RULES + " rules");
    List<BinRule> out = new ArrayList<>();
    for (int i = 0; i < node.size(); i++) {
      try {
        out.add(from(node.get(i)));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Rule " + (i + 1) + ": " + e.getMessage());
      }
    }
    return List.copyOf(out);
  }

  public static BinRule from(JsonNode node) {
    if (node == null || !node.isObject()) throw new IllegalArgumentException("A rule is a JSON object");
    String id = text(node, "id", "");
    if (!id.matches("[a-z0-9-]{1,40}")) id = UUID.randomUUID().toString().substring(0, 8);
    String name = text(node, "name", "").trim();
    if (name.length() > 80) throw new IllegalArgumentException("Keep the name under 80 characters");
    String description = text(node, "description", "").trim();
    if (description.length() > MAX_DESCRIPTION) throw new IllegalArgumentException("Keep the description under " + MAX_DESCRIPTION + " characters");
    String method = text(node, "method", "ANY").toUpperCase(Locale.ROOT);
    if (!METHODS.contains(method)) throw new IllegalArgumentException("Unknown method " + method);
    List<Condition> conditions = new ArrayList<>();
    JsonNode cs = node.path("conditions");
    if (cs.size() > MAX_CONDITIONS) throw new IllegalArgumentException("A rule has up to " + MAX_CONDITIONS + " conditions");
    for (JsonNode c : cs) conditions.add(condition(c));
    List<Answer> answers = new ArrayList<>();
    JsonNode as = node.path("answers");
    if (as.isEmpty()) throw new IllegalArgumentException("Give the rule an answer");
    if (as.size() > MAX_ANSWERS) throw new IllegalArgumentException("A rule has up to " + MAX_ANSWERS + " weighted answers");
    for (JsonNode a : as) answers.add(answer(a, as.size() > 1));
    return new BinRule(id, name, description, node.path("enabled").asBoolean(true), method, List.copyOf(conditions), List.copyOf(answers));
  }

  private static Condition condition(JsonNode c) {
    Target target = Target.of(text(c, "target", "path"));
    String key = text(c, "key", "").trim();
    if (target.needsKey() && key.isEmpty()) {
      throw new IllegalArgumentException(switch (target) {
        case JSON -> "A JSON condition needs a path like $.event";
        case XML -> "An XML condition needs an XPath like /order/id";
        default -> "A " + target.name().toLowerCase(Locale.ROOT) + " condition needs a name";
      });
    }
    if (target == Target.JSON && !key.startsWith("$")) throw new IllegalArgumentException("JSON paths start with $, like $.event");
    if (target == Target.XML) BinReads.checkXPath(key);
    Op op = Op.of(text(c, "op", "equals"));
    String value = op.needsValue() ? text(c, "value", "") : "";
    if (value.length() > 1000) throw new IllegalArgumentException("Keep condition values under 1000 characters");
    if (op == Op.TEMPLATE) {
      if (target != Target.PATH) throw new IllegalArgumentException("Only the path can match a pattern like /users/:id");
      BinPathTemplate.check(value);
    }
    if (op == Op.MATCHES) {
      try {
        Pattern.compile(value);
      } catch (PatternSyntaxException e) {
        throw new IllegalArgumentException("The pattern " + value + " isn't a valid regular expression");
      }
    }
    return new Condition(target, target.needsKey() ? key : "", op, value);
  }

  private static Answer answer(JsonNode a, boolean weighted) {
    int status = a.path("status").asInt(200);
    if (status < 100 || status > 599) throw new IllegalArgumentException("Use a status from 100 to 599");
    int delay = a.path("delayMs").asInt(0);
    if (delay < 0 || delay > MAX_DELAY_MS) throw new IllegalArgumentException("Delays go up to 15 seconds");
    int weight = weighted ? a.path("weight").asInt(1) : 1;
    if (weight < 1 || weight > 100) throw new IllegalArgumentException("Weights go from 1 to 100");
    String body = text(a, "body", "");
    String fileName = text(a, "fileName", "").trim();
    boolean template = a.path("template").asBoolean(false);
    if (fileName.isEmpty()) {
      if (body.length() > MAX_BODY) throw new IllegalArgumentException("Keep a body under 16 KB");
    } else {
      if (fileName.length() > 100 || fileName.contains("/") || fileName.contains("\\")) throw new IllegalArgumentException("Give the file a plain name under 100 characters");
      if (template) throw new IllegalArgumentException("A file is sent as it is, so it can't fill in dynamic values");
      if (decodedSize(body) > MAX_BODY) throw new IllegalArgumentException("Keep a file under 16 KB");
    }
    String note = text(a, "description", "").trim();
    if (note.length() > MAX_ANSWER_NOTE) throw new IllegalArgumentException("Keep an answer's description under " + MAX_ANSWER_NOTE + " characters");
    Map<String, String> headers = new LinkedHashMap<>();
    a.path("headers").fields().forEachRemaining(e -> {
      String name = e.getKey().trim();
      if (!name.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]{1,100}")) throw new IllegalArgumentException("Header name " + name + " isn't valid");
      headers.put(name, e.getValue().asText(""));
    });
    if (headers.size() > MAX_HEADERS) throw new IllegalArgumentException("Up to " + MAX_HEADERS + " headers per answer");
    return new Answer(weight, status, delay, Map.copyOf(headers), body, template, note, fileName);
  }

  public ObjectNode toJson() {
    ObjectNode out = Jsons.object().put("id", id).put("name", name).put("description", description).put("enabled", enabled).put("method", method);
    ArrayNode cs = out.putArray("conditions");
    for (Condition c : conditions) {
      ObjectNode n = cs.addObject().put("target", c.target().name().toLowerCase(Locale.ROOT)).put("op", c.op().name().toLowerCase(Locale.ROOT)).put("value", c.value());
      if (!c.key().isEmpty()) n.put("key", c.key());
    }
    ArrayNode as = out.putArray("answers");
    for (Answer a : answers) {
      ObjectNode n = as.addObject().put("weight", a.weight()).put("status", a.status()).put("delayMs", a.delayMs()).put("body", a.body()).put("template", a.template());
      if (!a.description().isEmpty()) n.put("description", a.description());
      if (a.file()) n.put("fileName", a.fileName());
      ObjectNode h = n.putObject("headers");
      new TreeMap<>(a.headers()).forEach(h::put);
    }
    return out;
  }

  public static ArrayNode toJson(List<BinRule> rules) {
    ArrayNode out = Jsons.MAPPER.createArrayNode();
    rules.forEach(r -> out.add(r.toJson()));
    return out;
  }

  public String label() {
    return name.isEmpty() ? method + " rule" : name;
  }

  private static int decodedSize(String base64) {
    try {
      return Base64.getDecoder().decode(base64).length;
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Send a file's body as base64");
    }
  }

  private static String text(JsonNode node, String field, String fallback) {
    JsonNode v = node.get(field);
    return v == null || v.isNull() ? fallback : v.asText(fallback);
  }
}
