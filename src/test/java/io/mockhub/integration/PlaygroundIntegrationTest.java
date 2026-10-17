package io.mockhub.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlaygroundIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private MockHubServer server;
  private WireMockServer partner;

  @AfterEach
  void stop() {
    if (server != null) server.close();
    if (partner != null) partner.stop();
  }

  @Test
  void callsTheProjectsOwnMocksAndKeepsHistory() {
    // Given
    server = MockHubServer.start(data);
    ApiClient api = server.api();
    String projectId = api.post(API + "/projects", "{\"name\":\"Pay\",\"slug\":\"pay\"}").json().get("id").asText();
    api.post(API + "/projects/" + projectId + "/stubs", "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/balance\"},\"response\":{\"status\":200,\"jsonBody\":{\"amount\":42}}}");

    // When
    JsonNode entry = api.post(API + "/playground/send", "{\"method\":\"GET\",\"url\":\"/pay/balance\",\"headers\":[{\"name\":\"Accept\",\"value\":\"application/json\"}]}").json();
    JsonNode history = api.get(API + "/playground/history").json();

    // Then
    assertThat(entry.get("local").asBoolean()).isTrue();
    assertThat(entry.at("/response/status").asInt()).isEqualTo(200);
    assertThat(entry.at("/response/body").asText()).contains("42");
    assertThat(history).hasSize(1);
    assertThat(api.delete(API + "/playground/history/" + entry.get("id").asText()).status()).isEqualTo(204);
    assertThat(api.get(API + "/playground/history").json()).isEmpty();
  }

  @Test
  void blocksPrivateAddressesByDefault() {
    // Given
    server = MockHubServer.start(data);

    // When
    ApiClient.Result result = server.api().post(API + "/playground/send", "{\"method\":\"GET\",\"url\":\"http://10.0.0.1/admin\"}");

    // Then
    assertThat(result.status()).isEqualTo(400);
    assertThat(result.json().get("error").asText()).contains("private or local");
  }

  @Test
  void callsOtherServicesWhenAllowedAndShowsTheResponse() {
    // Given
    partner = new WireMockServer(options().dynamicPort());
    partner.start();
    partner.stubFor(get(urlPathEqualTo("/users")).willReturn(aResponse().withStatus(201).withHeader("X-Real", "yes").withBody("[{\"id\":1}]")));
    server = MockHubServer.start(data, Map.of("MOCKHUB_PLAYGROUND_ALLOW_PRIVATE", "true"));

    // When
    JsonNode entry = server.api().post(API + "/playground/send", "{\"method\":\"GET\",\"url\":\"http://localhost:" + partner.port() + "/users\"}").json();

    // Then
    assertThat(entry.get("local").asBoolean()).isFalse();
    assertThat(entry.at("/response/status").asInt()).isEqualTo(201);
    assertThat(entry.at("/response/body").asText()).isEqualTo("[{\"id\":1}]");
    assertThat(entry.at("/response/headers").toString().toLowerCase()).contains("x-real");
  }

  @Test
  void viewersCannotUseThePlaygroundButCanGenerateData() {
    // Given
    server = MockHubServer.start(data, Map.of("MOCKHUB_USERS", "root:pw:admin,qa:pw:viewer"));
    ApiClient viewer = server.api().login("qa", "pw");

    // When
    int send = viewer.post(API + "/playground/send", "{\"url\":\"/x\"}").status();
    JsonNode generated = viewer.post(API + "/fake/generate", "{\"count\":2,\"fields\":[{\"name\":\"id\",\"expression\":\"sequence\"},{\"name\":\"email\",\"expression\":\"Internet.emailAddress\"}]}").json();

    // Then
    assertThat(send).isEqualTo(403);
    assertThat(generated.get("records")).hasSize(2);
    assertThat(generated.at("/records/1/id").asInt()).isEqualTo(2);
    assertThat(generated.at("/records/0/email").asText()).contains("@");
  }
}
