package io.mockhub.otel;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class OtlpJson {
  static final String SCOPE = "io.mockhub";
  private static final Pattern TRACEPARENT = Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-([0-9a-f]{16})-[0-9a-f]{2}$");
  private static final String ZERO_TRACE = "0".repeat(32);
  private static final String ZERO_SPAN = "0".repeat(16);

  record Request(long atMs, long durationMs, String method, String path, String query, int status, String clientIp,
      String projectId, String projectSlug, String stubId, String stubName, boolean matched, String traceId, String spanId, String parentSpanId) {}

  record Trace(String traceId, String parentSpanId) {}

  private OtlpJson() {}

  static Trace parent(String traceparent) {
    if (traceparent == null) return null;
    var m = TRACEPARENT.matcher(traceparent.trim().toLowerCase());
    if (!m.matches() || m.group(1).equals(ZERO_TRACE) || m.group(2).equals(ZERO_SPAN)) return null;
    return new Trace(m.group(1), m.group(2));
  }

  static ObjectNode resource(Map<String, String> attributes) {
    ObjectNode resource = Jsons.object();
    ArrayNode list = resource.putArray("attributes");
    attributes.forEach((k, v) -> list.add(kv(k, v)));
    return resource;
  }

  static ObjectNode kv(String key, Object value) {
    ObjectNode out = Jsons.object().put("key", key);
    ObjectNode v = out.putObject("value");
    if (value instanceof Boolean b) v.put("boolValue", b);
    else if (value instanceof Integer || value instanceof Long) v.put("intValue", String.valueOf(value));
    else if (value instanceof Double d) v.put("doubleValue", d);
    else v.put("stringValue", String.valueOf(value));
    return out;
  }

  static ArrayNode attributes(Request r) {
    ArrayNode list = Jsons.MAPPER.createArrayNode();
    list.add(kv("http.request.method", r.method()));
    list.add(kv("url.path", r.path()));
    if (r.query() != null && !r.query().isEmpty()) list.add(kv("url.query", r.query()));
    list.add(kv("http.response.status_code", r.status()));
    if (r.clientIp() != null && !r.clientIp().isEmpty()) list.add(kv("client.address", r.clientIp()));
    list.add(kv("mockhub.project.id", r.projectId()));
    list.add(kv("mockhub.project.slug", r.projectSlug()));
    list.add(kv("mockhub.matched", r.matched()));
    if (r.stubId() != null) list.add(kv("mockhub.stub.id", r.stubId()));
    if (r.stubName() != null && !r.stubName().isEmpty()) list.add(kv("mockhub.stub.name", r.stubName()));
    return list;
  }

  static String spanName(Request r) {
    return r.method() + " " + (r.matched() ? (r.stubName() == null || r.stubName().isEmpty() ? "/" + r.projectSlug() : r.stubName()) : "unmatched");
  }

  static ObjectNode traces(Map<String, String> resource, List<Request> requests, String version) {
    ObjectNode root = Jsons.object();
    ObjectNode rs = root.putArray("resourceSpans").addObject();
    rs.set("resource", resource(resource));
    ObjectNode ss = rs.putArray("scopeSpans").addObject();
    ss.putObject("scope").put("name", SCOPE).put("version", version);
    ArrayNode spans = ss.putArray("spans");
    for (Request r : requests) {
      ObjectNode span = spans.addObject()
          .put("traceId", r.traceId())
          .put("spanId", r.spanId())
          .put("name", spanName(r))
          .put("kind", 2)
          .put("startTimeUnixNano", nanos(r.atMs()))
          .put("endTimeUnixNano", nanos(r.atMs() + r.durationMs()));
      if (r.parentSpanId() != null) span.put("parentSpanId", r.parentSpanId());
      span.set("attributes", attributes(r));
      if (r.status() >= 500) span.putObject("status").put("code", 2).put("message", "HTTP " + r.status());
    }
    return root;
  }

  static ObjectNode logs(Map<String, String> resource, List<Request> requests, String version) {
    ObjectNode root = Jsons.object();
    ObjectNode rl = root.putArray("resourceLogs").addObject();
    rl.set("resource", resource(resource));
    ObjectNode sl = rl.putArray("scopeLogs").addObject();
    sl.putObject("scope").put("name", SCOPE).put("version", version);
    ArrayNode records = sl.putArray("logRecords");
    for (Request r : requests) {
      ObjectNode record = records.addObject()
          .put("timeUnixNano", nanos(r.atMs()))
          .put("observedTimeUnixNano", nanos(r.atMs() + r.durationMs()))
          .put("severityNumber", r.status() >= 500 ? 17 : r.status() >= 400 ? 13 : 9)
          .put("severityText", r.status() >= 500 ? "ERROR" : r.status() >= 400 ? "WARN" : "INFO")
          .put("traceId", r.traceId())
          .put("spanId", r.spanId());
      record.putObject("body").put("stringValue", r.method() + " " + r.path() + (r.query() == null || r.query().isEmpty() ? "" : "?" + r.query())
          + " → " + r.status() + " in " + r.durationMs() + " ms" + (r.matched() ? "" : " (no stub matched)"));
      record.set("attributes", attributes(r));
    }
    return root;
  }

  static String nanos(long millis) {
    return millis + "000000";
  }
}
