package io.mockhub.otel;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OtlpMetrics {
  static final int MAX_SERIES = 2000;
  static final double[] BOUNDS = {0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1, 2.5, 5, 7.5, 10};

  private record Key(String method, int status, String slug, boolean matched) {}

  private static final class Series {
    private long count;
    private double sum;
    private final long[] buckets = new long[BOUNDS.length + 1];
  }

  private final long startMs;
  private final Map<Key, Series> series = new LinkedHashMap<>();
  private long dropped;

  OtlpMetrics(long startMs) {
    this.startMs = startMs;
  }

  synchronized void record(OtlpJson.Request r) {
    Key key = new Key(r.method(), r.status(), r.projectSlug(), r.matched());
    Series s = series.get(key);
    if (s == null) {
      if (series.size() >= MAX_SERIES) {
        dropped++;
        return;
      }
      s = new Series();
      series.put(key, s);
    }
    double seconds = r.durationMs() / 1000.0;
    s.count++;
    s.sum += seconds;
    int bucket = 0;
    while (bucket < BOUNDS.length && seconds > BOUNDS[bucket]) bucket++;
    s.buckets[bucket]++;
  }

  synchronized boolean isEmpty() {
    return series.isEmpty();
  }

  synchronized long dropped() {
    return dropped;
  }

  synchronized ObjectNode snapshot(Map<String, String> resource, long nowMs, String version) {
    ObjectNode root = Jsons.object();
    ObjectNode rm = root.putArray("resourceMetrics").addObject();
    rm.set("resource", OtlpJson.resource(resource));
    ObjectNode sm = rm.putArray("scopeMetrics").addObject();
    sm.putObject("scope").put("name", OtlpJson.SCOPE).put("version", version);
    ArrayNode metrics = sm.putArray("metrics");
    ObjectNode counter = metrics.addObject().put("name", "mockhub.requests").put("unit", "{request}")
        .put("description", "Requests answered by MockHub mocks");
    ObjectNode sum = counter.putObject("sum").put("aggregationTemporality", 2).put("isMonotonic", true);
    ArrayNode sumPoints = sum.putArray("dataPoints");
    ObjectNode histogram = metrics.addObject().put("name", "http.server.request.duration").put("unit", "s")
        .put("description", "How long MockHub took to answer mock requests");
    ObjectNode hist = histogram.putObject("histogram").put("aggregationTemporality", 2);
    ArrayNode histPoints = hist.putArray("dataPoints");
    for (Map.Entry<Key, Series> e : series.entrySet()) {
      ArrayNode attributes = attributes(e.getKey());
      Series s = e.getValue();
      sumPoints.addObject().put("startTimeUnixNano", OtlpJson.nanos(startMs)).put("timeUnixNano", OtlpJson.nanos(nowMs))
          .put("asInt", String.valueOf(s.count)).set("attributes", attributes.deepCopy());
      ObjectNode point = histPoints.addObject().put("startTimeUnixNano", OtlpJson.nanos(startMs)).put("timeUnixNano", OtlpJson.nanos(nowMs))
          .put("count", String.valueOf(s.count)).put("sum", s.sum);
      ArrayNode counts = point.putArray("bucketCounts");
      for (long c : s.buckets) counts.add(String.valueOf(c));
      ArrayNode bounds = point.putArray("explicitBounds");
      for (double b : BOUNDS) bounds.add(b);
      point.set("attributes", attributes);
    }
    return root;
  }

  private static ArrayNode attributes(Key key) {
    ArrayNode list = Jsons.MAPPER.createArrayNode();
    for (ObjectNode kv : List.of(OtlpJson.kv("http.request.method", key.method()), OtlpJson.kv("http.response.status_code", key.status()),
        OtlpJson.kv("mockhub.project.slug", key.slug()), OtlpJson.kv("mockhub.matched", key.matched()))) list.add(kv);
    return list;
  }
}
