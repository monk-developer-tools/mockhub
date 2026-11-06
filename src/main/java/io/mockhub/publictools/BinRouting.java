package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public record BinRouting(Mode mode, String target, String pathPrefix, boolean insecure, Map<String, String> requestHeaders,
    Map<String, String> responseHeaders) {
  public static final BinRouting DEFAULT = new BinRouting(Mode.MOCK, "", "", false, Map.of(), Map.of());

  public enum Mode {
    MOCK, FORWARD, TUNNEL
  }

  public static BinRouting from(JsonNode node) {
    if (node == null || !node.isObject()) throw new IllegalArgumentException("Send routing as a JSON object");
    Mode mode;
    try {
      mode = Mode.valueOf(node.path("mode").asText("mock").toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Routing is mock, forward or tunnel");
    }
    String target = node.path("target").asText("").trim();
    while (target.endsWith("/")) target = target.substring(0, target.length() - 1);
    if (mode == Mode.FORWARD) checkTarget(target);
    String prefix = node.path("pathPrefix").asText("").trim();
    while (prefix.endsWith("/")) prefix = prefix.substring(0, prefix.length() - 1);
    if (!prefix.isEmpty() && (!prefix.startsWith("/") || prefix.length() > 200 || prefix.contains("?") || prefix.contains("#"))) {
      throw new IllegalArgumentException("A path prefix starts with / and has no ? or #, like /api/v1");
    }
    return new BinRouting(mode, target, prefix, node.path("insecure").asBoolean(false), headers(node.path("requestHeaders")),
        headers(node.path("responseHeaders")));
  }

  private static void checkTarget(String target) {
    URI uri;
    try {
      uri = URI.create(target);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("The target " + target + " isn't a valid URL");
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
      throw new IllegalArgumentException("Forward to a URL with http:// or https://, like https://api.example.com");
    }
    if (uri.getRawQuery() != null || uri.getRawFragment() != null) throw new IllegalArgumentException("Leave the query out of the target; put a path in the prefix");
  }

  private static Map<String, String> headers(JsonNode node) {
    Map<String, String> out = new LinkedHashMap<>();
    node.fields().forEachRemaining(e -> {
      String name = e.getKey().trim();
      if (!name.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]{1,100}")) throw new IllegalArgumentException("Header name " + name + " isn't valid");
      out.put(name, e.getValue().asText(""));
    });
    if (out.size() > 20) throw new IllegalArgumentException("Up to 20 headers each way");
    return Map.copyOf(out);
  }

  public URI forwardUri(String path, String rawQuery) {
    return URI.create(target + pathPrefix + path + (rawQuery == null || rawQuery.isEmpty() ? "" : "?" + rawQuery));
  }

  public ObjectNode toJson() {
    ObjectNode out = Jsons.object().put("mode", mode.name().toLowerCase(Locale.ROOT)).put("target", target).put("pathPrefix", pathPrefix).put("insecure", insecure);
    ObjectNode req = out.putObject("requestHeaders");
    new TreeMap<>(requestHeaders).forEach(req::put);
    ObjectNode res = out.putObject("responseHeaders");
    new TreeMap<>(responseHeaders).forEach(res::put);
    return out;
  }
}
