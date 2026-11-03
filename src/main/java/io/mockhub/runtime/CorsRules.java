package io.mockhub.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public record CorsRules(List<String> origins, List<String> methods, List<String> headers, boolean credentials, boolean simulateFailure) {
  public static final List<String> ALL_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
  private static final Set<String> METHODS = Set.copyOf(ALL_METHODS);
  private static final Pattern ORIGIN = Pattern.compile("\\*|https?://(\\*\\.)?[A-Za-z0-9.-]+(:\\d{1,5})?");
  private static final Pattern HEADER = Pattern.compile("\\*|[A-Za-z0-9!#$%&'*+.^_`|~-]{1,64}");
  private static final int MAX_ENTRIES = 50;

  public CorsRules {
    origins = origins == null ? List.of() : List.copyOf(origins);
    methods = methods == null || methods.isEmpty() ? ALL_METHODS : List.copyOf(methods);
    headers = headers == null ? List.of() : List.copyOf(headers);
  }

  static CorsRules parse(JsonNode node) {
    if (Fields.absent(node)) return null;
    List<String> origins = list(node.get("origins"), "origin", ORIGIN, false);
    List<String> methods = list(node.get("methods"), "method", null, true);
    methods.forEach(m -> {
      if (!METHODS.contains(m)) throw ApiException.badRequest("'" + m + "' isn't an HTTP method CORS can allow");
    });
    List<String> headers = list(node.get("headers"), "header", HEADER, false);
    boolean failure = node.path("simulateFailure").asBoolean(false);
    if (origins.isEmpty() && !failure) throw ApiException.badRequest("Add at least one allowed origin, or * for any");
    return new CorsRules(origins, methods, headers, node.path("credentials").asBoolean(false), failure);
  }

  private static List<String> list(JsonNode node, String label, Pattern pattern, boolean upper) {
    List<String> out = new ArrayList<>();
    if (node == null || !node.isArray()) return out;
    for (JsonNode item : node) {
      String value = item.asText("").trim().replaceAll("/+$", "");
      if (value.isEmpty()) continue;
      if (upper) value = value.toUpperCase(Locale.ROOT);
      if (pattern != null && !pattern.matcher(value).matches()) throw ApiException.badRequest("'" + value + "' isn't a valid " + label);
      if (!out.contains(value)) out.add(value);
    }
    if (out.size() > MAX_ENTRIES) throw ApiException.badRequest("CORS can list at most " + MAX_ENTRIES + " " + label + "s");
    return out;
  }

  public boolean allowsOrigin(String origin) {
    if (origin == null || simulateFailure) return false;
    for (String allowed : origins) {
      if (allowed.equals("*") || allowed.equalsIgnoreCase(origin)) return true;
      if (allowed.contains("://*.")) {
        String scheme = allowed.substring(0, allowed.indexOf("://") + 3);
        String suffix = allowed.substring(scheme.length() + 1);
        if (origin.regionMatches(true, 0, scheme, 0, scheme.length()) && origin.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))
            && origin.length() > scheme.length() + suffix.length()) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean anyOrigin() {
    return origins.contains("*") && !credentials;
  }
}
