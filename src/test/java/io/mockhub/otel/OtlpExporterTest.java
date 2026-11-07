package io.mockhub.otel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import io.mockhub.history.HistoryEntry;
import io.mockhub.playground.Header;
import io.mockhub.playground.HttpSender;
import io.mockhub.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OtlpExporterTest {

  private record Post(String url, List<Header> headers, JsonNode body) {}

  private final List<Post> posts = new ArrayList<>();
  private final HttpSender http = (uri, method, headers, body, timeout, max) -> {
    posts.add(new Post(uri.toString(), headers, Jsons.MAPPER.readTree(body)));
    return new HttpSender.Raw(200, List.of(), new byte[0], false);
  };
  private final OtlpConfig config = OtlpConfig.from(Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://collector:4318",
      "OTEL_EXPORTER_OTLP_HEADERS", "authorization=Bearer t", "OTEL_SERVICE_NAME", "mocks"));
  private final OtlpExporter exporter = new OtlpExporter(config, http, () -> 1_700_000_000_000L, "1.2.3");
  private final Project project = new Project("p1", "Orders", "orders", "", "", "", "", "o1", "t1", null, false, null, List.of());

  private static HistoryEntry entry(String url, int status, long ms, boolean matched, List<Header> headers) {
    return new HistoryEntry("e1", 1_700_000_000_000L, "p1", "POST", url, "http://h" + url, "203.0.113.4", headers, "", false, status, List.of(), "",
        false, matched, matched ? "s1" : null, matched ? "Create order" : null, ms);
  }

  private Post post(String suffix) {
    return posts.stream().filter(p -> p.url().endsWith(suffix)).findFirst().orElseThrow();
  }

  @Test
  void sendsASpanAndALogPerRequestThatContinueTheCallersTrace() {
    // Given
    String parent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
    exporter.served(project, entry("/orders/orders?x=1", 201, 12, true, List.of(new Header("traceparent", parent))));
    exporter.served(project, entry("/orders/missing", 502, 3, false, List.of()));

    // When
    exporter.flush();

    // Then
    JsonNode spans = post("/v1/traces").body().at("/resourceSpans/0/scopeSpans/0/spans");
    assertThat(spans).hasSize(2);
    assertThat(spans.get(0).get("traceId").asText()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
    assertThat(spans.get(0).get("parentSpanId").asText()).isEqualTo("b7ad6b7169203331");
    assertThat(spans.get(0).get("name").asText()).isEqualTo("POST Create order");
    assertThat(spans.get(0).get("startTimeUnixNano").asText()).isEqualTo("1700000000000000000");
    assertThat(spans.get(0).get("endTimeUnixNano").asText()).isEqualTo("1700000000012000000");
    assertThat(spans.get(0).get("attributes").toString()).contains("\"url.path\"").contains("/orders/orders").contains("\"mockhub.project.slug\"");
    assertThat(spans.get(1).get("traceId").asText()).hasSize(32).isNotEqualTo("0af7651916cd43dd8448eb211c80319c");
    assertThat(spans.get(1).has("parentSpanId")).isFalse();
    assertThat(spans.get(1).get("name").asText()).isEqualTo("POST unmatched");
    assertThat(spans.get(1).at("/status/code").asInt()).isEqualTo(2);
    assertThat(post("/v1/traces").body().at("/resourceSpans/0/resource/attributes").toString()).contains("mocks");
    JsonNode logs = post("/v1/logs").body().at("/resourceLogs/0/scopeLogs/0/logRecords");
    assertThat(logs.get(0).at("/body/stringValue").asText()).isEqualTo("POST /orders/orders?x=1 → 201 in 12 ms");
    assertThat(logs.get(1).get("severityText").asText()).isEqualTo("ERROR");
    assertThat(logs.get(1).at("/body/stringValue").asText()).endsWith("(no stub matched)");
    assertThat(logs.get(0).get("spanId").asText()).isEqualTo(spans.get(0).get("spanId").asText());
    assertThat(post("/v1/traces").headers()).contains(new Header("authorization", "Bearer t"), new Header("Content-Type", "application/json"));
  }

  @Test
  void sendsCumulativeCountsAndADurationHistogram() {
    // Given
    exporter.served(project, entry("/orders/a", 200, 4, true, List.of()));
    exporter.served(project, entry("/orders/b", 200, 30, true, List.of()));
    exporter.served(project, entry("/orders/c", 404, 1, false, List.of()));

    // When
    exporter.exportMetrics();
    exporter.exportMetrics();

    // Then
    JsonNode metrics = posts.get(posts.size() - 1).body().at("/resourceMetrics/0/scopeMetrics/0/metrics");
    JsonNode counter = metrics.get(0);
    assertThat(counter.get("name").asText()).isEqualTo("mockhub.requests");
    assertThat(counter.at("/sum/aggregationTemporality").asInt()).isEqualTo(2);
    assertThat(counter.at("/sum/dataPoints")).hasSize(2);
    assertThat(counter.at("/sum/dataPoints/0/asInt").asText()).isEqualTo("2");
    JsonNode histogram = metrics.get(1);
    assertThat(histogram.get("name").asText()).isEqualTo("http.server.request.duration");
    JsonNode point = histogram.at("/histogram/dataPoints/0");
    assertThat(point.get("count").asText()).isEqualTo("2");
    assertThat(point.get("sum").asDouble()).isEqualTo(0.034, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(point.get("bucketCounts").get(0).asText()).isEqualTo("1");
    assertThat(point.get("bucketCounts").get(3).asText()).isEqualTo("1");
    assertThat(point.get("explicitBounds")).hasSize(OtlpMetrics.BOUNDS.length);
  }

  @Test
  void dropsWhatDoesNotFitAndSendsNothingWhenThereIsNothing() {
    // Given
    for (int i = 0; i < OtlpExporter.MAX_QUEUE + 5; i++) exporter.served(project, entry("/orders/x", 200, 1, true, List.of()));

    // When
    long dropped = exporter.dropped();
    exporter.flush();
    int sent = posts.size();
    exporter.flush();

    // Then
    assertThat(dropped).isEqualTo(5);
    assertThat(sent).isEqualTo(2 * ((OtlpExporter.MAX_QUEUE + OtlpExporter.BATCH - 1) / OtlpExporter.BATCH));
    assertThat(posts).hasSize(sent);
    assertThat(OtlpExporter.start(OtlpConfig.OFF, http, "1")).isSameAs(io.mockhub.history.ServedRequestListener.NONE);
  }
}
