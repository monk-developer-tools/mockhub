package io.mockhub.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.mockhub.api.Jsons;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenTelemetryIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private WireMockServer collector;
  private MockHubServer server;

  @BeforeEach
  void start() {
    collector = new WireMockServer(options().dynamicPort());
    collector.start();
    collector.stubFor(post(urlPathMatching("/v1/.*")).willReturn(aResponse().withStatus(200)));
    server = MockHubServer.start(data, Map.of(
        "OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:" + collector.port(),
        "OTEL_EXPORTER_OTLP_HEADERS", "x-team=mocks",
        "OTEL_SERVICE_NAME", "mockhub-test",
        "OTEL_BSP_SCHEDULE_DELAY", "100",
        "OTEL_METRIC_EXPORT_INTERVAL", "150"));
  }

  @AfterEach
  void stop() {
    server.close();
    collector.stop();
  }

  private List<JsonNode> received(String signal) {
    return collector.findAll(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlPathMatching("/v1/" + signal))).stream()
        .map(LoggedRequest::getBodyAsString).map(body -> {
          try {
            return Jsons.MAPPER.readTree(body);
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        }).toList();
  }

  private void waitFor(String signal, String text) throws InterruptedException {
    for (int i = 0; i < 100 && received(signal).stream().noneMatch(b -> b.toString().contains(text)); i++) Thread.sleep(50);
  }

  @Test
  void exportsATraceLogAndMetricsForEveryMockRequest() throws InterruptedException {
    // Given
    ApiClient api = server.api();
    String projectId = api.post(API + "/projects", "{\"name\":\"Shop\",\"slug\":\"shop\"}").json().get("id").asText();
    api.post(API + "/projects/" + projectId + "/stubs", "{\"name\":\"Get cart\",\"request\":{\"method\":\"GET\",\"urlPath\":\"/cart\"},\"response\":{\"status\":200,\"body\":\"{}\"}}");

    // When
    api.send("GET", "/shop/cart", null, null, Map.of("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"));
    api.get("/shop/nothing");
    waitFor("traces", "unmatched");
    waitFor("logs", "no stub matched");
    waitFor("metrics", "http.server.request.duration");

    // Then
    String traces = received("traces").toString();
    assertThat(traces).contains("4bf92f3577b34da6a3ce929d0e0e4736").contains("00f067aa0ba902b7").contains("GET Get cart").contains("GET unmatched")
        .contains("mockhub-test").contains("\"shop\"");
    assertThat(received("logs").toString()).contains("GET /shop/cart → 200").contains("(no stub matched)");
    assertThat(received("metrics").toString()).contains("mockhub.requests").contains("http.server.request.duration");
    assertThat(collector.findAll(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlPathMatching("/v1/traces"))).get(0)
        .getHeader("x-team")).isEqualTo("mocks");
    assertThat(traces).doesNotContain("__admin");
  }

  @Test
  void sendsWhatIsLeftWhenTheServerStopsAndNothingAfter() throws InterruptedException {
    // Given
    server.close();
    server = MockHubServer.start(data, Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:" + collector.port(),
        "OTEL_BSP_SCHEDULE_DELAY", "60000", "OTEL_METRIC_EXPORT_INTERVAL", "60000"));
    ApiClient api = server.api();
    api.post(API + "/projects", "{\"name\":\"Late\",\"slug\":\"late\"}");
    api.get("/late/anything");
    collector.resetRequests();

    // When
    server.close();
    int atClose = collector.getAllServeEvents().size();
    Thread.sleep(300);
    int later = collector.getAllServeEvents().size();
    server = MockHubServer.start(data, Map.of());

    // Then
    assertThat(received("traces").toString()).contains("late");
    assertThat(received("metrics").toString()).contains("mockhub.requests");
    assertThat(later).isEqualTo(atClose);
  }
}
