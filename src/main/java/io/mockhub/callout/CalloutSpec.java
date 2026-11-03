package io.mockhub.callout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CalloutSpec(String method, String url, Map<String, String> headers, String body, long delayMs, boolean forward) {
  public CalloutSpec {
    method = method == null || method.isBlank() ? "POST" : method.trim();
    url = url == null ? "" : url.trim();
    headers = headers == null ? Map.of() : Map.copyOf(headers);
    body = body == null ? "" : body;
    delayMs = Math.max(0, delayMs);
  }

  public static CalloutSpec from(Map<String, ?> parameters) {
    if (parameters == null) return new CalloutSpec(null, null, null, null, 0, false);
    return new CalloutSpec(
        text(parameters.get("method")),
        text(parameters.get("url")),
        headers(parameters.get("headers")),
        text(parameters.get("body")),
        delay(parameters.get("delay")),
        Boolean.parseBoolean(String.valueOf(parameters.get("forward"))));
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }

  private static Map<String, String> headers(Object value) {
    Map<String, String> out = new LinkedHashMap<>();
    if (value instanceof Map<?, ?> map) {
      map.forEach((k, v) -> {
        if (k == null || v == null) return;
        out.put(k.toString(), v instanceof List<?> list ? String.join(", ", list.stream().map(String::valueOf).toList()) : v.toString());
      });
    }
    return out;
  }

  private static long delay(Object value) {
    if (value instanceof Number n) return n.longValue();
    if (value instanceof Map<?, ?> map && map.get("milliseconds") instanceof Number n) return n.longValue();
    if (value instanceof String s && s.matches("\\d+")) return Long.parseLong(s);
    return 0;
  }
}
