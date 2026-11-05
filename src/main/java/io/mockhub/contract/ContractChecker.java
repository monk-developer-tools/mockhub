package io.mockhub.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.mockhub.api.Jsons;
import io.mockhub.history.HistoryEntry;
import io.mockhub.playground.Header;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class ContractChecker {
  private static final Set<String> IGNORED_HEADER_PREFIXES = Set.of("x-forwarded-", "x-real-ip", "x-request-id", "x-amzn-", "x-b3-",
      "x-cloud-trace", "x-envoy-", "x-mockhub-", "x-datadog-", "x-correlation-id", "x-requested-with", "x-original-");
  private static final Pattern INDEX = Pattern.compile("\\[\\d+]");
  private static final Pattern ID_SEGMENT = Pattern.compile("\\d+|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|[0-9a-fA-F]{16,}");

  public List<Drift> check(ApiContract contract, HistoryEntry entry, String path) {
    List<Drift> out = new ArrayList<>();
    String method = entry.method().toUpperCase(Locale.ROOT);
    ApiContract.Match match = contract.match(method, path);
    if (!match.pathKnown()) {
      out.add(new Drift(DriftKind.UNDOCUMENTED_PATH, method, generalise(path), ""));
      return out;
    }
    if (match.operation() == null) {
      if (method.equals("OPTIONS")) return out;
      out.add(new Drift(DriftKind.UNDOCUMENTED_METHOD, method, match.template(), "documented: " + String.join(", ", match.methods())));
      return out;
    }
    ApiContract.Operation op = match.operation();
    checkParameters(contract, entry, op, match, out);
    checkRequestBody(contract, entry, op, out);
    if (entry.matched() && entry.status() > 0) checkResponse(contract, entry, op, out);
    return out;
  }

  private void checkParameters(ApiContract contract, HistoryEntry entry, ApiContract.Operation op, ApiContract.Match match, List<Drift> out) {
    Map<String, String> query = query(entry.url());
    for (String name : query.keySet()) {
      boolean documented = op.params().stream().anyMatch(p -> p.in().equals("query") && p.name().equals(name)) || contract.documentsQuery(name);
      if (!documented) out.add(drift(DriftKind.UNDOCUMENTED_QUERY_PARAMETER, op, name));
    }
    for (Header h : entry.requestHeaders()) {
      String lower = h.name().toLowerCase(Locale.ROOT);
      if (!lower.startsWith("x-") || IGNORED_HEADER_PREFIXES.stream().anyMatch(lower::startsWith) || contract.documentsHeader(lower)) continue;
      boolean documented = op.params().stream().anyMatch(p -> p.in().equals("header") && p.name().equalsIgnoreCase(h.name()));
      if (!documented) out.add(drift(DriftKind.UNDOCUMENTED_HEADER, op, h.name()));
    }
    for (ApiContract.Param p : op.params()) {
      Optional<String> value = switch (p.in()) {
        case "query" -> Optional.ofNullable(query.get(p.name()));
        case "header" -> header(entry.requestHeaders(), p.name());
        case "path" -> Optional.ofNullable(match.pathValues().get(p.name()));
        default -> Optional.empty();
      };
      if (!p.in().equals("query") && !p.in().equals("header") && !p.in().equals("path")) continue;
      if (value.isEmpty()) {
        if (p.required()) out.add(drift(DriftKind.MISSING_PARAMETER, op, p.in() + " " + p.name()));
        continue;
      }
      if (p.schema() == null) continue;
      for (String message : contract.validate(p.schema(), coerce(value.get(), p.schema()))) {
        out.add(drift(DriftKind.INVALID_PARAMETER, op, p.in() + " " + p.name() + ": " + tidy(message)));
      }
    }
  }

  private void checkRequestBody(ApiContract contract, HistoryEntry entry, ApiContract.Operation op, List<Drift> out) {
    boolean hasBody = !entry.requestBody().isEmpty();
    ApiContract.Body body = op.requestBody();
    if (body == null) {
      if (hasBody) out.add(drift(DriftKind.UNDOCUMENTED_REQUEST_BODY, op, ""));
      return;
    }
    if (!hasBody) {
      if (body.required()) out.add(drift(DriftKind.MISSING_REQUEST_BODY, op, ""));
      return;
    }
    String type = baseType(header(entry.requestHeaders(), "Content-Type").orElse(""));
    Optional<String> media = mediaFor(body.media(), type);
    if (media.isEmpty()) {
      out.add(drift(DriftKind.UNDOCUMENTED_REQUEST_CONTENT_TYPE, op, type.isEmpty() ? "none" : type));
      return;
    }
    JsonNode schema = body.media().get(media.get());
    if (schema == null || !isJson(type) || entry.requestBodyTruncated()) return;
    schemaDrift(contract, schema, entry.requestBody(), "").forEach(d -> out.add(drift(DriftKind.REQUEST_BODY_SCHEMA, op, d)));
  }

  private void checkResponse(ApiContract contract, HistoryEntry entry, ApiContract.Operation op, List<Drift> out) {
    String status = String.valueOf(entry.status());
    Map<String, JsonNode> media = op.responses().get(status);
    if (media == null) media = op.responses().get(status.charAt(0) + "XX");
    if (media == null) media = op.responses().get("DEFAULT");
    if (media == null) {
      out.add(drift(DriftKind.UNDOCUMENTED_STATUS, op, status));
      return;
    }
    if (entry.responseBody().isEmpty()) return;
    String type = baseType(header(entry.responseHeaders(), "Content-Type").orElse(""));
    if (media.isEmpty()) {
      out.add(drift(DriftKind.UNDOCUMENTED_RESPONSE_CONTENT_TYPE, op, status + " " + (type.isEmpty() ? "body" : type) + " (the contract describes no body)"));
      return;
    }
    Optional<String> chosen = mediaFor(media, type);
    if (chosen.isEmpty()) {
      out.add(drift(DriftKind.UNDOCUMENTED_RESPONSE_CONTENT_TYPE, op, status + " " + (type.isEmpty() ? "none" : type)));
      return;
    }
    JsonNode schema = media.get(chosen.get());
    if (schema == null || !isJson(type) || entry.responseBodyTruncated()) return;
    schemaDrift(contract, schema, entry.responseBody(), status + " ").forEach(d -> out.add(drift(DriftKind.RESPONSE_BODY_SCHEMA, op, d)));
  }

  private static List<String> schemaDrift(ApiContract contract, JsonNode schema, String body, String prefix) {
    JsonNode value;
    try {
      value = Jsons.MAPPER.readTree(body);
    } catch (Exception e) {
      return List.of(prefix + "body isn't valid JSON");
    }
    return contract.validate(schema, value).stream().map(m -> prefix + tidy(m)).distinct().toList();
  }

  private static Drift drift(DriftKind kind, ApiContract.Operation op, String detail) {
    return new Drift(kind, op.method(), op.path(), detail);
  }

  static String tidy(String message) {
    return INDEX.matcher(message).replaceAll("[*]");
  }

  static String generalise(String path) {
    StringBuilder out = new StringBuilder();
    for (String segment : path.split("/", -1)) {
      if (out.length() > 0 || !segment.isEmpty()) out.append('/');
      if (segment.isEmpty()) continue;
      out.append(ID_SEGMENT.matcher(segment).matches() ? "{id}" : segment);
    }
    return out.length() == 0 ? "/" : out.toString();
  }

  static Optional<String> mediaFor(Map<String, JsonNode> media, String type) {
    if (media.containsKey(type)) return Optional.of(type);
    String major = type.contains("/") ? type.substring(0, type.indexOf('/')) : type;
    for (String candidate : media.keySet()) {
      String base = baseType(candidate);
      if (base.equals(type)) return Optional.of(candidate);
      if (base.equals(major + "/*") || base.equals("*/*")) return Optional.of(candidate);
    }
    return Optional.empty();
  }

  static String baseType(String contentType) {
    int semi = contentType.indexOf(';');
    return (semi < 0 ? contentType : contentType.substring(0, semi)).trim().toLowerCase(Locale.ROOT);
  }

  private static boolean isJson(String type) {
    return type.equals("application/json") || type.endsWith("+json");
  }

  private static Optional<String> header(List<Header> headers, String name) {
    return headers.stream().filter(h -> h.name().equalsIgnoreCase(name)).map(Header::value).findFirst();
  }

  static Map<String, String> query(String url) {
    Map<String, String> out = new LinkedHashMap<>();
    int q = url.indexOf('?');
    if (q < 0) return out;
    for (String pair : url.substring(q + 1).split("&")) {
      if (pair.isEmpty()) continue;
      int eq = pair.indexOf('=');
      String name = decode(eq < 0 ? pair : pair.substring(0, eq));
      String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
      if (name.endsWith("[]")) name = name.substring(0, name.length() - 2);
      out.putIfAbsent(name, value);
    }
    return out;
  }

  private static String decode(String value) {
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return value;
    }
  }

  static JsonNode coerce(String value, JsonNode schema) {
    String type = schema.path("type").isArray() ? schema.path("type").path(0).asText("") : schema.path("type").asText("");
    try {
      return switch (type) {
        case "integer" -> value.matches("-?\\d+") ? JsonNodeFactory.instance.numberNode(new java.math.BigInteger(value)) : JsonNodeFactory.instance.textNode(value);
        case "number" -> JsonNodeFactory.instance.numberNode(new java.math.BigDecimal(value));
        case "boolean" -> value.equals("true") || value.equals("false") ? JsonNodeFactory.instance.booleanNode(Boolean.parseBoolean(value)) : JsonNodeFactory.instance.textNode(value);
        case "array" -> {
          var array = JsonNodeFactory.instance.arrayNode();
          for (String part : value.split(",")) array.add(coerce(part, schema.path("items")));
          yield array;
        }
        default -> JsonNodeFactory.instance.textNode(value);
      };
    } catch (NumberFormatException e) {
      return JsonNodeFactory.instance.textNode(value);
    }
  }
}
