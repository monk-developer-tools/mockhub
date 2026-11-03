package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient api;
  private String projectId;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data);
    api = server.api();
    projectId = api.post(API + "/projects", "{\"name\":\"Pay\",\"slug\":\"pay\"}").json().get("id").asText();
    api.post(API + "/projects/" + projectId + "/stubs",
        "{\"name\":\"Take payment\",\"request\":{\"method\":\"POST\",\"urlPath\":\"/payments\"},\"response\":{\"status\":201,\"jsonBody\":{\"id\":\"pay_1\"}}}");
    api.post(API + "/projects/" + projectId + "/stubs",
        "{\"name\":\"Status\",\"request\":{\"method\":\"GET\",\"urlPath\":\"/status\"},\"response\":{\"status\":200,\"body\":\"up\"}}");
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private JsonNode history(String query) {
    return api.get(API + "/projects/" + projectId + "/history" + query).json();
  }

  private JsonNode waitFor(String query, int count) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
    JsonNode page = history(query);
    while (page.path("total").asInt() < count && Instant.now().isBefore(deadline)) {
      Thread.sleep(50);
      page = history(query);
    }
    return page;
  }

  @Test
  void recordsEveryCallWithBodiesAndFindsItAgain() throws InterruptedException {
    // Given
    api.send("POST", "/pay/payments", "{\"amount\":42,\"currency\":\"EUR\"}", "application/json", Map.of("X-Trace", "trace-7"));
    api.get("/pay/status");
    api.get("/pay/missing");

    // When
    JsonNode all = waitFor("", 3);
    JsonNode posts = history("?method=post&q=eur&header=X-Trace:trace-7");
    JsonNode missing = history("?matched=false&status=4xx");
    JsonNode detail = api.get(API + "/projects/" + projectId + "/history/" + posts.at("/items/0/id").asText()).json();

    // Then
    assertThat(all.path("total").asInt()).isEqualTo(3);
    assertThat(all.at("/facets/methods/GET").asInt()).isEqualTo(2);
    assertThat(all.at("/facets/statuses/2xx").asInt()).isEqualTo(2);
    assertThat(all.at("/facets/unmatched").asInt()).isEqualTo(1);
    assertThat(all.at("/items/0/url").asText()).isEqualTo("/pay/missing");
    assertThat(posts.path("total").asInt()).isEqualTo(1);
    assertThat(posts.at("/items/0/stubName").asText()).isEqualTo("Take payment");
    assertThat(posts.at("/items/0/requestBody").asText()).isEmpty();
    assertThat(missing.at("/items/0/url").asText()).isEqualTo("/pay/missing");
    assertThat(detail.at("/entry/requestBody").asText()).contains("\"currency\":\"EUR\"");
    assertThat(detail.at("/entry/responseBody").asText()).contains("pay_1");
    assertThat(detail.at("/entry/status").asInt()).isEqualTo(201);
  }

  @Test
  void keepsTheHistoryAcrossARestart() throws InterruptedException {
    // Given
    api.get("/pay/status");
    waitFor("", 1);
    server.close();

    // When
    server = MockHubServer.start(data);
    api = server.api();
    JsonNode page = history("");

    // Then
    assertThat(page.path("total").asInt()).isEqualTo(1);
    assertThat(page.at("/items/0/url").asText()).isEqualTo("/pay/status");
  }

  @Test
  void linksCalloutsToTheRequestThatSentThem() throws InterruptedException {
    // Given
    api.post(API + "/projects/" + projectId + "/stubs", """
        {"name":"Notify","request":{"method":"POST","urlPath":"/notify"},"response":{"status":202},
         "serveEventListeners":[{"name":"mockhub-callout","parameters":{"method":"POST","url":"%s/pay/status","body":"hello"}}]}"""
        .formatted(server.baseUrl()));
    api.post("/pay/notify", "{}");
    JsonNode notify = waitFor("?q=notify", 1);
    waitFor("?q=status", 1);

    // When
    JsonNode detail = api.get(API + "/projects/" + projectId + "/history/" + notify.at("/items/0/id").asText()).json();

    // Then
    assertThat(detail.path("callouts")).hasSize(1);
    assertThat(detail.at("/callouts/0/request/url").asText()).endsWith("/pay/status");
  }

  @Test
  void exportsAHarFileAndCountsTraffic() throws InterruptedException {
    // Given
    api.post("/pay/payments?source=web", "{\"amount\":1}");
    api.get("/pay/status");
    waitFor("", 2);

    // When
    ApiClient.Result har = api.get(API + "/projects/" + projectId + "/history-export?method=POST");
    JsonNode traffic = api.get(API + "/projects/" + projectId + "/traffic?window=5m").json();
    JsonNode daily = api.get(API + "/projects/" + projectId + "/traffic/daily").json();

    // Then
    assertThat(har.header("Content-Disposition")).contains("pay-").contains(".har");
    JsonNode entry = har.json().at("/log/entries/0");
    assertThat(entry.at("/request/method").asText()).isEqualTo("POST");
    assertThat(entry.at("/request/queryString/0/name").asText()).isEqualTo("source");
    assertThat(entry.at("/request/postData/text").asText()).isEqualTo("{\"amount\":1}");
    assertThat(entry.at("/response/status").asInt()).isEqualTo(201);
    assertThat(traffic.path("total").asInt()).isEqualTo(2);
    assertThat(traffic.path("points")).hasSize(300);
    assertThat(traffic.path("peakRps").asInt()).isPositive();
    assertThat(daily).hasSize(30);
    assertThat(daily.get(29).path("requests").asInt()).isEqualTo(2);
  }

  @Test
  void clearingTheLogClearsTheHistory() throws InterruptedException {
    // Given
    api.get("/pay/status");
    waitFor("", 1);

    // When
    api.send("DELETE", API + "/projects/" + projectId + "/requests", null, null);
    JsonNode page = history("");

    // Then
    assertThat(page.path("total").asInt()).isZero();
  }

  @Test
  void rejectsAStatusFilterItCannotRead() {
    // When
    ApiClient.Result result = api.get(API + "/projects/" + projectId + "/history?status=teapot");

    // Then
    assertThat(result.status()).isEqualTo(400);
    assertThat(result.body()).contains("404, 4xx or 400-499");
  }
}
