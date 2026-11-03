package io.mockhub.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CorsHeaders {
  public static final CorsRules PERMISSIVE = new CorsRules(List.of("*"), CorsRules.ALL_METHODS, List.of("*"), true, false);
  private static final String MAX_AGE = "1800";

  private final boolean permissiveByDefault;

  public CorsHeaders(boolean permissiveByDefault) {
    this.permissiveByDefault = permissiveByDefault;
  }

  public CorsRules rulesFor(RuntimeSettings settings) {
    CorsRules own = RuntimeSettings.of(settings).cors();
    if (own != null) return own;
    return permissiveByDefault ? PERMISSIVE : null;
  }

  public Map<String, String> forResponse(CorsRules rules, String origin) {
    Map<String, String> headers = new LinkedHashMap<>();
    if (rules == null || !rules.allowsOrigin(origin)) return headers;
    headers.put("Access-Control-Allow-Origin", rules.anyOrigin() ? "*" : origin);
    if (rules.credentials()) headers.put("Access-Control-Allow-Credentials", "true");
    headers.put("Access-Control-Expose-Headers", "*");
    if (!rules.anyOrigin()) headers.put("Vary", "Origin");
    return headers;
  }

  public Map<String, String> forPreflight(CorsRules rules, String origin, String requestedHeaders) {
    Map<String, String> headers = forResponse(rules, origin);
    if (headers.isEmpty()) return headers;
    headers.remove("Access-Control-Expose-Headers");
    headers.put("Access-Control-Allow-Methods", String.join(",", rules.methods()));
    String allowed = rules.headers().isEmpty() || rules.headers().contains("*")
        ? (requestedHeaders == null ? "" : requestedHeaders)
        : String.join(",", rules.headers());
    if (!allowed.isBlank()) headers.put("Access-Control-Allow-Headers", allowed);
    headers.put("Access-Control-Max-Age", MAX_AGE);
    return headers;
  }
}
