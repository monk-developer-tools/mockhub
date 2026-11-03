package io.mockhub.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.mockhub.api.Jsons;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CalloutIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private MockHubServer server;
  private WireMockServer partner;

  @BeforeEach
  void startPartner() {
    partner = new WireMockServer(options().dynamicPort());
    partner.start();
    partner.stubFor(post(urlPathEqualTo("/callbacks")).willReturn(aResponse().withStatus(204)));
    partner.stubFor(post(urlPathEqualTo("/authorize")).willReturn(aResponse().withStatus(201)
        .withHeader("Content-Type", "application/json").withHeader("X-Issuer", "acme")
        .withBody("{\"approved\":true}")));
    partner.stubFor(any(urlPathEqualTo("/forward")).willReturn(aResponse().withStatus(200)));
  }

  @AfterEach
  void stop() {
    if (server != null) server.close();
    partner.stop();
  }

  private static JsonNode json(String text) {
    try {
      return Jsons.MAPPER.readTree(text);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String project(ApiClient api) {
    return api.post(API + "/projects", "{\"name\":\"Pay\",\"slug\":\"pay\"}").json().get("id").asText();
  }

  private JsonNode waitForCallouts(ApiClient api, String projectId, int count) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
    JsonNode log = api.get(API + "/projects/" + projectId + "/callouts").json();
    while (log.size() < count && Instant.now().isBefore(deadline)) {
      Thread.sleep(50);
      log = api.get(API + "/projects/" + projectId + "/callouts").json();
    }
    return log;
  }

  @Test
  void sendsATemplatedCalloutAfterAnsweringAndLogsIt() throws InterruptedException {
    // Given
    server = MockHubServer.start(data, Map.of("MOCKHUB_CALLOUT_ALLOW_PRIVATE", "true"));
    ApiClient api = server.api();
    String projectId = project(api);
    api.post(API + "/projects/" + projectId + "/stubs", """
        {"name":"Take payment","request":{"method":"POST","urlPath":"/payments"},
         "response":{"status":202,"jsonBody":{"reference":"R-9"}},
         "serveEventListeners":[{"name":"mockhub-callout","parameters":{"method":"POST","url":"http://localhost:%d/callbacks",
           "headers":{"Content-Type":"application/json"},
           "body":"{\\"paymentId\\":\\"{{jsonPath originalRequest.body '$.id'}}\\",\\"reference\\":\\"{{jsonPath originalResponse.body '$.reference'}}\\",\\"payer\\":\\"{{fake 'Name.firstName'}}\\"}",
           "delay":{"type":"fixed","milliseconds":20}}}]}""".formatted(partner.port()));

    // When
    ApiClient.Result response = api.post("/pay/payments", "{\"id\":\"pay_42\"}");
    JsonNode log = waitForCallouts(api, projectId, 1);

    // Then
    assertThat(response.status()).isEqualTo(202);
    assertThat(log).hasSize(1);
    JsonNode entry = log.get(0);
    assertThat(entry.get("mode").asText()).isEqualTo("async");
    assertThat(entry.get("stubName").asText()).isEqualTo("Take payment");
    assertThat(entry.at("/trigger/url").asText()).isEqualTo("/pay/payments");
    assertThat(entry.at("/response/status").asInt()).isEqualTo(204);
    JsonNode sent = json(entry.at("/request/body").asText());
    assertThat(sent.get("paymentId").asText()).isEqualTo("pay_42");
    assertThat(sent.get("reference").asText()).isEqualTo("R-9");
    assertThat(sent.get("payer").asText()).isNotBlank().doesNotContain("{{").doesNotContain("fake");
    partner.verify(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlPathEqualTo("/callbacks"))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(equalToJson("{\"paymentId\":\"pay_42\",\"reference\":\"R-9\"}", false, true)));
  }

  @Test
  void forwardsTheIncomingRequestAsIs() throws InterruptedException {
    // Given
    server = MockHubServer.start(data, Map.of("MOCKHUB_CALLOUT_ALLOW_PRIVATE", "true"));
    ApiClient api = server.api();
    String projectId = project(api);
    api.post(API + "/projects/" + projectId + "/stubs", """
        {"request":{"method":"PUT","urlPath":"/events"},"response":{"status":202},
         "serveEventListeners":[{"name":"mockhub-callout","parameters":{"url":"http://localhost:%d/forward","forward":true}}]}"""
        .formatted(partner.port()));

    // When
    api.put("/pay/events", "{\"type\":\"order.created\"}");
    waitForCallouts(api, projectId, 1);

    // Then
    partner.verify(putRequestedFor(urlPathEqualTo("/forward")).withRequestBody(equalToJson("{\"type\":\"order.created\"}")));
  }

  @Test
  void waitsForTheCalloutAndAnswersWithItsResponse() {
    // Given
    server = MockHubServer.start(data, Map.of("MOCKHUB_CALLOUT_ALLOW_PRIVATE", "true"));
    ApiClient api = server.api();
    String projectId = project(api);
    api.post(API + "/projects/" + projectId + "/stubs", """
        {"name":"Authorize via issuer","request":{"method":"POST","urlPath":"/authorize"},
         "response":{"status":200,"transformers":["mockhub-sync-callout"],
           "transformerParameters":{"callout":{"method":"POST","url":"http://localhost:%d/authorize",
             "headers":{"Content-Type":"application/json"},"body":"{\\"card\\":\\"{{jsonPath originalRequest.body '$.card'}}\\"}"}}}}"""
        .formatted(partner.port()));

    // When
    ApiClient.Result response = api.post("/pay/authorize", "{\"card\":\"4242\"}");

    // Then
    assertThat(response.status()).isEqualTo(201);
    assertThat(response.header("X-Issuer")).isEqualTo("acme");
    assertThat(response.json().get("approved").asBoolean()).isTrue();
    partner.verify(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlPathEqualTo("/authorize"))
        .withRequestBody(equalToJson("{\"card\":\"4242\"}")));
    JsonNode log = api.get(API + "/projects/" + projectId + "/callouts").json();
    assertThat(log).singleElement().satisfies(e -> assertThat(e.get("mode").asText()).isEqualTo("sync"));
  }

  @Test
  void blocksPrivateTargetsByDefaultButAlwaysReachesItsOwnMocks() throws InterruptedException {
    // Given
    server = MockHubServer.start(data);
    ApiClient api = server.api();
    String projectId = project(api);
    api.post(API + "/projects/" + projectId + "/stubs", """
        {"request":{"method":"POST","urlPath":"/sync"},
         "response":{"status":200,"transformers":["mockhub-sync-callout"],
           "transformerParameters":{"callout":{"method":"POST","url":"http://127.0.0.2:%d/authorize"}}}}""".formatted(partner.port()));
    api.post(API + "/projects/" + projectId + "/stubs", """
        {"request":{"method":"POST","urlPath":"/notify"},"response":{"status":202},
         "serveEventListeners":[{"name":"mockhub-callout","parameters":{"method":"POST","url":"%s/pay/receiver","body":"hello"}}]}"""
        .formatted(server.baseUrl()));
    api.post(API + "/projects/" + projectId + "/stubs", "{\"request\":{\"method\":\"POST\",\"urlPath\":\"/receiver\"},\"response\":{\"status\":204}}");

    // When
    ApiClient.Result blocked = api.post("/pay/sync", "{}");
    api.post("/pay/notify", "{}");
    JsonNode log = waitForCallouts(api, projectId, 2);

    // Then
    assertThat(blocked.status()).isEqualTo(502);
    assertThat(blocked.header("X-MockHub-Callout-Error")).isEqualTo("true");
    assertThat(blocked.json().get("error").asText()).contains("MOCKHUB_CALLOUT_ALLOW_PRIVATE=true");
    partner.verify(0, anyRequestedFor(urlPathEqualTo("/authorize")));
    assertThat(log).anySatisfy(e -> assertThat(e.path("error").asText()).contains("private or local"));
    assertThat(log).anySatisfy(e -> {
      assertThat(e.at("/request/url").asText()).endsWith("/pay/receiver");
      assertThat(e.at("/response/status").asInt()).isEqualTo(204);
    });
  }

  @Test
  void clearsTheLogAndExportsCalloutsAsWireMockWebhooks() throws InterruptedException {
    // Given
    server = MockHubServer.start(data, Map.of("MOCKHUB_CALLOUT_ALLOW_PRIVATE", "true"));
    ApiClient api = server.api();
    String projectId = project(api);
    api.post(API + "/projects/" + projectId + "/stubs", """
        {"request":{"method":"POST","urlPath":"/hook"},"response":{"status":200},
         "serveEventListeners":[{"name":"mockhub-callout","parameters":{"method":"POST","url":"http://localhost:%d/callbacks"}}]}"""
        .formatted(partner.port()));
    api.post("/pay/hook", "{}");
    waitForCallouts(api, projectId, 1);

    // When
    api.delete(API + "/projects/" + projectId + "/callouts");
    JsonNode exported = json(api.get(API + "/projects/" + projectId + "/export/wiremock").body());

    // Then
    assertThat(api.get(API + "/projects/" + projectId + "/callouts").json()).isEmpty();
    assertThat(exported.at("/mappings/0/serveEventListeners/0/name").asText()).isEqualTo("webhook");
  }
}
