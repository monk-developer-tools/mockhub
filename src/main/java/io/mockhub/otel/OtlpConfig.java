package io.mockhub.otel;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record OtlpConfig(String tracesUrl, String metricsUrl, String logsUrl, Map<String, String> headers, Map<String, String> resource,
    long batchDelayMs, long metricIntervalMs, String protocol) {

  public static final OtlpConfig OFF = new OtlpConfig("", "", "", Map.of(), Map.of(), 5000, 60000, "http/json");

  public static OtlpConfig from(Map<String, String> env) {
    String base = trim(env.get("OTEL_EXPORTER_OTLP_ENDPOINT"));
    Map<String, String> headers = pairs(env.get("OTEL_EXPORTER_OTLP_HEADERS"));
    Map<String, String> resource = pairs(env.get("OTEL_RESOURCE_ATTRIBUTES"));
    String service = trim(env.get("OTEL_SERVICE_NAME"));
    resource.put("service.name", service.isEmpty() ? resource.getOrDefault("service.name", "mockhub") : service);
    return new OtlpConfig(
        url(env, base, "TRACES", "traces"),
        url(env, base, "METRICS", "metrics"),
        url(env, base, "LOGS", "logs"),
        Map.copyOf(headers),
        Map.copyOf(resource),
        millis(env.get("OTEL_BSP_SCHEDULE_DELAY"), 5000),
        millis(env.get("OTEL_METRIC_EXPORT_INTERVAL"), 60000),
        trim(env.getOrDefault("OTEL_EXPORTER_OTLP_PROTOCOL", "http/json")).toLowerCase(Locale.ROOT));
  }

  public boolean enabled() {
    return !tracesUrl.isEmpty() || !metricsUrl.isEmpty() || !logsUrl.isEmpty();
  }

  private static String url(Map<String, String> env, String base, String signal, String path) {
    String exporter = trim(env.get("OTEL_" + signal + "_EXPORTER"));
    if (exporter.equalsIgnoreCase("none")) return "";
    String own = trim(env.get("OTEL_EXPORTER_OTLP_" + signal + "_ENDPOINT"));
    if (!own.isEmpty()) return own;
    if (base.isEmpty()) return "";
    return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/v1/" + path;
  }

  static Map<String, String> pairs(String text) {
    Map<String, String> out = new LinkedHashMap<>();
    if (text == null) return out;
    for (String pair : text.split(",")) {
      int eq = pair.indexOf('=');
      if (eq <= 0) continue;
      out.put(decode(pair.substring(0, eq).trim()), decode(pair.substring(eq + 1).trim()));
    }
    return out;
  }

  private static String decode(String value) {
    return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
  }

  private static long millis(String value, long fallback) {
    try {
      return value == null || value.isBlank() ? fallback : Math.max(10, Long.parseLong(value.trim()));
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static String trim(String value) {
    return value == null ? "" : value.trim();
  }
}
