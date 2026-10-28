package io.mockhub.stub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class StubPaths {
  private static final String[] LITERAL_FIELDS = {"url", "urlPath", "urlPathTemplate"};
  private static final String[] REGEX_FIELDS = {"urlPattern", "urlPathPattern"};

  private StubPaths() {}

  public static void toAbsolute(ObjectNode stub, String basePath) {
    stripBasePathBeforeProxying(stub, basePath);
    ObjectNode request = request(stub);
    boolean hasUrl = false;
    for (String f : LITERAL_FIELDS) {
      if (request.hasNonNull(f)) {
        request.put(f, joinLiteral(basePath, request.get(f).asText()));
        hasUrl = true;
      }
    }
    for (String f : REGEX_FIELDS) {
      if (request.hasNonNull(f)) {
        request.put(f, joinRegex(basePath, request.get(f).asText()));
        hasUrl = true;
      }
    }
    if (!hasUrl) {
      request.put("urlPattern", catchAll(basePath));
    }
  }

  public static void toRelative(ObjectNode stub, String basePath) {
    hideDefaultProxyPrefix(stub, basePath);
    ObjectNode request = request(stub);
    if (request.path("urlPattern").asText("").equals(catchAll(basePath))) {
      request.remove("urlPattern");
      return;
    }
    for (String f : LITERAL_FIELDS) {
      if (request.hasNonNull(f)) request.put(f, stripLiteral(basePath, request.get(f).asText()));
    }
    for (String f : REGEX_FIELDS) {
      if (request.hasNonNull(f)) request.put(f, stripRegex(basePath, request.get(f).asText()));
    }
  }

  private static String catchAll(String basePath) {
    return basePath.isEmpty() ? "/.*" : basePath + "(/.*)?";
  }

  private static void stripBasePathBeforeProxying(ObjectNode stub, String basePath) {
    if (basePath.isEmpty()) return;
    if (stub.get("response") instanceof ObjectNode response && response.hasNonNull("proxyBaseUrl") && !response.has("proxyUrlPrefixToRemove")) {
      response.put("proxyUrlPrefixToRemove", basePath);
    }
  }

  private static void hideDefaultProxyPrefix(ObjectNode stub, String basePath) {
    if (stub.get("response") instanceof ObjectNode response && basePath.equals(response.path("proxyUrlPrefixToRemove").asText(null))) {
      response.remove("proxyUrlPrefixToRemove");
    }
  }

  public static String displayUrl(JsonNode request) {
    for (String f : LITERAL_FIELDS) {
      if (request.hasNonNull(f)) return request.get(f).asText();
    }
    for (String f : REGEX_FIELDS) {
      if (request.hasNonNull(f)) return request.get(f).asText();
    }
    return "*";
  }

  static String joinLiteral(String base, String path) {
    if (path.isEmpty() || path.equals("/")) return base.isEmpty() ? "/" : base;
    if (path.startsWith("?")) return base + path;
    return base + (path.startsWith("/") ? path : "/" + path);
  }

  static String stripLiteral(String base, String path) {
    if (base.isEmpty()) return path;
    if (path.equals(base)) return "/";
    if (path.startsWith(base + "/")) return path.substring(base.length());
    if (path.startsWith(base + "?")) return "/" + path.substring(base.length());
    return path;
  }

  static String joinRegex(String base, String regex) {
    if (regex.startsWith("^")) return "^" + joinRegex(base, regex.substring(1));
    if (regex.startsWith("/")) return base + regex;
    return base + "/" + regex;
  }

  static String stripRegex(String base, String regex) {
    if (base.isEmpty()) return regex;
    if (regex.startsWith("^")) return "^" + stripRegex(base, regex.substring(1));
    if (regex.startsWith(base + "/")) return regex.substring(base.length());
    return regex;
  }

  private static ObjectNode request(ObjectNode stub) {
    JsonNode r = stub.get("request");
    if (r instanceof ObjectNode o) return o;
    ObjectNode created = stub.putObject("request");
    created.put("method", "ANY");
    return created;
  }
}
