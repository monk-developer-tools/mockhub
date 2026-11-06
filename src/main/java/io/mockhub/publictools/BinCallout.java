package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public record BinCallout(boolean async, String method, String url, boolean original, String body, Map<String, String> headers, Auth auth,
    int minDelayMs, int maxDelayMs) {
  public static final int MAX_TRIGGER_MS = 60_000;
  public static final int MAX_URL = 2000;
  static final String SAME = "SAME";

  public enum AuthKind {
    NONE, BEARER, BASIC, HEADER
  }

  public record Auth(AuthKind kind, String name, String value) {
    static final Auth NONE = new Auth(AuthKind.NONE, "", "");
  }

  public static BinCallout from(JsonNode node) {
    if (!node.isObject()) throw new IllegalArgumentException("A callout is a JSON object");
    String mode = node.path("mode").asText("sync").toLowerCase(Locale.ROOT);
    if (!mode.equals("sync") && !mode.equals("async")) throw new IllegalArgumentException("A callout is sync or async");
    boolean async = mode.equals("async");
    String method = node.path("method").asText(SAME).trim().toUpperCase(Locale.ROOT);
    if (!method.equals(SAME) && (method.equals("ANY") || !BinRule.METHODS.contains(method))) {
      throw new IllegalArgumentException("Call out with the same method as the request, or GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS, QUERY or TRACE");
    }
    String url = node.path("url").asText("").trim();
    if (url.isEmpty()) throw new IllegalArgumentException("Give the callout a URL, like https://api.example.com/hook");
    if (url.length() > MAX_URL) throw new IllegalArgumentException("Keep the callout URL under " + MAX_URL + " characters");
    if (!url.toLowerCase(Locale.ROOT).startsWith("http://") && !url.toLowerCase(Locale.ROOT).startsWith("https://")) throw new IllegalArgumentException("The callout URL starts with http:// or https://");
    String payload = node.path("payload").asText("original").toLowerCase(Locale.ROOT);
    if (!payload.equals("original") && !payload.equals("custom")) throw new IllegalArgumentException("A callout sends the original payload or a custom one");
    boolean original = payload.equals("original");
    String body = original ? "" : node.path("body").asText("");
    if (body.length() > BinRule.MAX_BODY) throw new IllegalArgumentException("Keep the callout body under 16 KB");
    int min = async ? node.path("minDelayMs").asInt(0) : 0;
    int max = async ? node.path("maxDelayMs").asInt(min) : 0;
    if (min < 0 || max > MAX_TRIGGER_MS) throw new IllegalArgumentException("A callout waits up to 60 seconds before it is sent");
    if (max < min) throw new IllegalArgumentException("The longest wait can't be shorter than the shortest");
    return new BinCallout(async, method, url, original, body, headers(node.path("headers")), auth(node.path("auth")), min, max);
  }

  private static Map<String, String> headers(JsonNode node) {
    Map<String, String> out = new LinkedHashMap<>();
    node.fields().forEachRemaining(e -> {
      String name = e.getKey().trim();
      if (!name.matches(PublicResponses.TOKEN)) throw new IllegalArgumentException("Header name " + name + " isn't valid");
      out.put(name, e.getValue().asText(""));
    });
    if (out.size() > BinRule.MAX_HEADERS) throw new IllegalArgumentException("Up to " + BinRule.MAX_HEADERS + " callout headers");
    return Map.copyOf(out);
  }

  private static Auth auth(JsonNode node) {
    if (node.isMissingNode() || node.isNull()) return Auth.NONE;
    AuthKind kind;
    try {
      kind = AuthKind.valueOf(node.path("type").asText("none").toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Callout auth is none, bearer, basic or header");
    }
    String name = node.path("name").asText("").trim();
    String value = node.path("value").asText("");
    if (value.length() > 1000 || name.length() > 200) throw new IllegalArgumentException("Keep the callout's credentials under 1000 characters");
    return switch (kind) {
      case NONE -> Auth.NONE;
      case BEARER -> {
        if (value.isBlank()) throw new IllegalArgumentException("Give the callout a bearer token");
        yield new Auth(kind, "", value);
      }
      case BASIC -> {
        if (name.isEmpty() || name.contains(":")) throw new IllegalArgumentException("Give the callout a user name without a colon");
        yield new Auth(kind, name, value);
      }
      case HEADER -> {
        if (!name.matches(PublicResponses.TOKEN)) throw new IllegalArgumentException("Name the callout's auth header with letters, digits and dashes, like X-Api-Key");
        if (value.isEmpty()) throw new IllegalArgumentException("Give the callout's auth header a value");
        yield new Auth(kind, name, value);
      }
    };
  }

  public ObjectNode toJson() {
    ObjectNode out = Jsons.object().put("mode", async ? "async" : "sync").put("method", method.equals(SAME) ? "same" : method).put("url", url)
        .put("payload", original ? "original" : "custom").put("body", body);
    ObjectNode h = out.putObject("headers");
    new TreeMap<>(headers).forEach(h::put);
    out.putObject("auth").put("type", auth.kind().name().toLowerCase(Locale.ROOT)).put("name", auth.name()).put("value", auth.value());
    if (async) out.put("minDelayMs", minDelayMs).put("maxDelayMs", maxDelayMs);
    return out;
  }
}
