package io.mockhub.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebhookIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private MockHubServer server;
  private WireMockServer partner;

  @BeforeEach
  void start() {
    partner = new WireMockServer(options().dynamicPort());
    partner.start();
    partner.stubFor(post(urlPathEqualTo("/callbacks")).willReturn(aResponse().withStatus(204)));
    server = MockHubServer.start(data);
  }

  @AfterEach
  void stop() {
    server.close();
    partner.stop();
  }

  @Test
  void aStubCallsAWebhookWithDataFromTheRequest() throws InterruptedException {
    // Given
    ApiClient api = server.api();
    String projectId = api.post(API + "/projects", "{\"name\":\"Pay\",\"slug\":\"pay\"}").json().get("id").asText();
    api.post(API + "/projects/" + projectId + "/stubs", """
        {"request":{"method":"POST","urlPath":"/payments"},
         "response":{"status":202,"jsonBody":{"status":"PENDING"}},
         "serveEventListeners":[{"name":"webhook","parameters":{"method":"POST","url":"http://localhost:%d/callbacks",
           "headers":{"Content-Type":"application/json"},
           "body":"{\\"paymentId\\":\\"{{jsonPath originalRequest.body '$.id'}}\\",\\"status\\":\\"SETTLED\\"}",
           "delay":{"type":"fixed","milliseconds":50}}}]}""".formatted(partner.port()));

    // When
    ApiClient.Result response = api.post("/pay/payments", "{\"id\":\"pay_42\"}");
    Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
    while (partner.findAll(postRequestedFor(urlPathEqualTo("/callbacks"))).isEmpty() && Instant.now().isBefore(deadline)) {
      Thread.sleep(50);
    }

    // Then
    assertThat(response.status()).isEqualTo(202);
    partner.verify(postRequestedFor(urlPathEqualTo("/callbacks"))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(equalToJson("{\"paymentId\":\"pay_42\",\"status\":\"SETTLED\"}")));
  }
}
