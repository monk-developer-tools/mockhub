package io.mockhub.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectionIntegrationTest {

  private static final String API = "/__admin/mockhub/api";
  private static final String TOKEN = "tok-abcdefghijklmnop";

  @TempDir
  Path data;
  private MockHubServer server;
  private WireMockServer partner;
  private ApiClient api;
  private String projectId;
  private String connections;

  @BeforeEach
  void start() {
    partner = new WireMockServer(options().dynamicPort());
    partner.start();
    partner.stubFor(post(urlPathEqualTo("/oauth/token")).willReturn(aResponse().withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"access_token\":\"" + TOKEN + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    partner.stubFor(any(urlPathEqualTo("/accounts")).atPriority(5).willReturn(aResponse().withStatus(401)));
    partner.stubFor(any(urlPathEqualTo("/accounts")).atPriority(1).withHeader("Authorization", equalTo("Bearer " + TOKEN))
        .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"balance\":12}")));
    server = MockHubServer.start(data, Map.of("MOCKHUB_CALLOUT_ALLOW_PRIVATE", "true"));
    api = server.api();
    JsonNode project = api.post(API + "/projects", "{\"name\":\"Pay\",\"slug\":\"pay\"}").json();
    projectId = project.get("id").asText();
    connections = API + "/orgs/" + project.get("orgId").asText() + "/connections";
  }

  @AfterEach
  void stop() {
    server.close();
    partner.stop();
  }

  private void bank() {
    ApiClient.Result created = api.post(connections, """
        {"key":"bank","name":"Bank API","type":"oauth2","tokenUrl":"http://localhost:%d/oauth/token","clientId":"mockhub","secret":"s3cret-value"}"""
        .formatted(partner.port()));
    assertThat(created.status()).as(created.body()).isEqualTo(201);
  }

  private JsonNode waitForCallouts(int count) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
    JsonNode log = api.get(API + "/projects/" + projectId + "/callouts").json();
    while (log.size() < count && Instant.now().isBefore(deadline)) {
      Thread.sleep(50);
      log = api.get(API + "/projects/" + projectId + "/callouts").json();
    }
    return log;
  }

  @Test
  void signsCalloutsWithACachedTokenAndHidesItInTheLog() {
    // Given
    bank();
    api.post(API + "/projects/" + projectId + "/stubs", """
        {"request":{"method":"GET","urlPath":"/balance"},
         "response":{"status":200,"transformers":["mockhub-sync-callout"],
           "transformerParameters":{"callout":{"method":"GET","url":"http://localhost:%d/accounts","connection":"bank"}}}}"""
        .formatted(partner.port()));

    // When
    ApiClient.Result first = api.get("/pay/balance");
    ApiClient.Result second = api.get("/pay/balance");

    // Then
    assertThat(first.status()).as(first.body()).isEqualTo(200);
    assertThat(second.json().get("balance").asInt()).isEqualTo(12);
    partner.verify(1, postRequestedFor(urlPathEqualTo("/oauth/token")));
    JsonNode entry = api.get(API + "/projects/" + projectId + "/callouts").json().get(0);
    assertThat(entry.at("/request/headers").toString()).contains("connection bank").doesNotContain(TOKEN);
    assertThat(api.get(connections).body()).doesNotContain("s3cret-value");
  }

  @Test
  void answersWithAClearErrorWhenTheConnectionIsMissing() {
    // Given
    api.post(API + "/projects/" + projectId + "/stubs", """
        {"request":{"method":"GET","urlPath":"/balance"},
         "response":{"status":200,"transformers":["mockhub-sync-callout"],
           "transformerParameters":{"callout":{"method":"GET","url":"http://localhost:%d/accounts","connection":"bank"}}}}"""
        .formatted(partner.port()));

    // When
    ApiClient.Result response = api.get("/pay/balance");

    // Then
    assertThat(response.status()).isEqualTo(502);
    assertThat(response.body()).contains("There is no connection called bank");
  }

  @Test
  void retriesAFailedWebhookAndLogsEachAttempt() throws InterruptedException {
    // Given
    partner.stubFor(post(urlPathEqualTo("/hooks")).inScenario("flaky").whenScenarioStateIs(Scenario.STARTED)
        .willReturn(aResponse().withStatus(503)).willSetStateTo("up"));
    partner.stubFor(post(urlPathEqualTo("/hooks")).inScenario("flaky").whenScenarioStateIs("up")
        .willReturn(aResponse().withStatus(204)));
    api.post(API + "/projects/" + projectId + "/stubs", """
        {"request":{"method":"POST","urlPath":"/orders"},"response":{"status":202},
         "serveEventListeners":[{"name":"mockhub-callout","parameters":{"url":"http://localhost:%d/hooks","body":"{}",
           "retry":{"count":3,"backoffMs":50}}}]}""".formatted(partner.port()));

    // When
    api.post("/pay/orders", "{}");
    JsonNode log = waitForCallouts(2);

    // Then
    assertThat(log).hasSize(2);
    assertThat(log.findValuesAsText("attempt")).containsExactlyInAnyOrder("1", "2");
    assertThat(log.findValues("response").stream().map(r -> r.get("status").asInt()).toList()).containsExactlyInAnyOrder(503, 204);
  }

  @Test
  void addsTheTokenToProxiedRequestsOnceAndKeepsOtherProxyHeaders() {
    // Given
    bank();
    partner.stubFor(get(urlPathMatching(".*/ledger/1")).willReturn(aResponse().withStatus(200).withBody("ledger")));
    api.post(API + "/projects/" + projectId + "/stubs", """
        {"request":{"method":"GET","urlPathPattern":"/ledger/.*"},
         "response":{"proxyBaseUrl":"http://localhost:%d","additionalProxyRequestHeaders":{"X-Trace":"t-1","Authorization":"Bearer old"},
           "transformers":["mockhub-connection"],"transformerParameters":{"connection":"bank"}}}""".formatted(partner.port()));

    // When
    ApiClient.Result response = api.get("/pay/ledger/1");

    // Then
    assertThat(response.status()).as(response.body()).isEqualTo(200);
    List<LoggedRequest> proxied = partner.findAll(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathMatching(".*/ledger/1")));
    assertThat(proxied).hasSize(1);
    assertThat(proxied.get(0).getHeaders().getHeader("Authorization").values()).containsExactly("Bearer " + TOKEN);
    assertThat(proxied.get(0).getHeader("X-Trace")).isEqualTo("t-1");
  }
}
