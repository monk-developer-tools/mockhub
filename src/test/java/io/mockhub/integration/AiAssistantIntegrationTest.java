package io.mockhub.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.mockhub.support.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.mockhub.api.Jsons;
import io.mockhub.support.FakeLlmTransport;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiAssistantIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private WireMockServer llm;
  private MockHubServer server;
  private ApiClient admin;
  private String projectId;

  @BeforeEach
  void start() {
    llm = new WireMockServer(options().dynamicPort());
    llm.start();
    server = MockHubServer.start(data, Map.of("MOCKHUB_USERS", "root:pw:admin,qa:pw:viewer"));
    admin = server.api().login("root", "pw");
    projectId = admin.post(API + "/projects", "{\"name\":\"Orders\",\"slug\":\"orders\"}").json().get("id").asText();
    ApiClient.Result saved = admin.put(API + "/ai/settings", "{\"enabled\":true,\"provider\":\"openai\",\"baseUrl\":\"http://localhost:" + llm.port()
        + "/v1\",\"apiKey\":\"sk-test\",\"model\":\"test-model\"}");
    assertThat(saved.status()).as(saved.body()).isEqualTo(200);
  }

  @AfterEach
  void stop() {
    server.close();
    llm.stop();
  }

  private void scriptConversation() {
    JsonNode toolCall = FakeLlmTransport.toolCall("call_1", "create_stub", json(
        "{'stub':{'name':'Get order','request':{'method':'GET','urlPathTemplate':'/orders/{id}'},"
            + "'response':{'status':200,'jsonBody':{'id':'{{request.path.id}}','state':'PAID'},'transformers':['response-template']}},"
            + "'summary':'Returns an order by id'}"));
    ObjectNode answer = Jsons.object();
    answer.putArray("choices").addObject().putObject("message").put("role", "assistant").put("content", "Staged GET /orders/{id}.");
    llm.stubFor(post(urlPathEqualTo("/v1/chat/completions")).inScenario("chat").whenScenarioStateIs(Scenario.STARTED)
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(Jsons.write(toolCall)))
        .willSetStateTo("answered"));
    llm.stubFor(post(urlPathEqualTo("/v1/chat/completions")).inScenario("chat").whenScenarioStateIs("answered")
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(Jsons.write(answer))));
  }

  @Test
  void chatStagesStubsThatApplyingTurnsIntoWorkingMocks() {
    // Given
    scriptConversation();

    // When
    ApiClient.Result chat = admin.post(API + "/projects/" + projectId + "/ai/chat",
        "{\"messages\":[{\"role\":\"user\",\"content\":\"Add GET /orders/{id} returning a paid order\"}]}");
    JsonNode changes = chat.json().get("changes");
    ApiClient.Result applied = admin.post(API + "/projects/" + projectId + "/ai/apply", "{\"changes\":" + Jsons.write(changes) + "}");

    // Then
    assertThat(chat.status()).as(chat.body()).isEqualTo(200);
    assertThat(chat.json().get("reply").asText()).isEqualTo("Staged GET /orders/{id}.");
    assertThat(changes).hasSize(1);
    assertThat(applied.json().get("applied").asInt()).isEqualTo(1);
    JsonNode order = server.api().get("/orders/orders/42").json();
    assertThat(order.get("id").asText()).isEqualTo("42");
    assertThat(order.get("state").asText()).isEqualTo("PAID");
    llm.verify(postRequestedFor(urlPathEqualTo("/v1/chat/completions"))
        .withHeader("Authorization", equalTo("Bearer sk-test"))
        .withRequestBody(containing("\"create_stub\""))
        .withRequestBody(containing("base path /orders")));
  }

  @Test
  void viewersCanAskButNotApply() {
    // Given
    scriptConversation();
    ApiClient qa = server.api().login("qa", "pw");

    // When
    ApiClient.Result chat = qa.post(API + "/projects/" + projectId + "/ai/chat", "{\"messages\":[{\"role\":\"user\",\"content\":\"Add an order\"}]}");

    // Then
    assertThat(chat.status()).isEqualTo(200);
    assertThat(qa.post(API + "/projects/" + projectId + "/ai/apply", "{\"changes\":" + Jsons.write(chat.json().get("changes")) + "}").status()).isEqualTo(403);
    assertThat(qa.get(API + "/ai/settings").status()).isEqualTo(403);
    assertThat(qa.get(API + "/ai/status").json().get("enabled").asBoolean()).isTrue();
  }

  @Test
  void hidesTheKeyAndTestsTheConnection() {
    // Given
    llm.stubFor(get(urlPathEqualTo("/v1/models")).willReturn(aResponse().withHeader("Content-Type", "application/json")
        .withBody("{\"data\":[{\"id\":\"test-model\"},{\"id\":\"other\"}]}")));

    // When
    JsonNode settings = admin.get(API + "/ai/settings").json();
    JsonNode test = admin.post(API + "/ai/test", "{}").json();

    // Then
    assertThat(settings.has("apiKey")).isFalse();
    assertThat(settings.get("apiKeySet").asBoolean()).isTrue();
    assertThat(settings.get("providers")).hasSize(3);
    assertThat(test.get("ok").asBoolean()).isTrue();
    assertThat(test.get("modelFound").asBoolean()).isTrue();
    llm.verify(getRequestedFor(urlPathEqualTo("/v1/models")).withHeader("Authorization", equalTo("Bearer sk-test")));
  }

  @Test
  void reportsProviderErrorsAsBadGateway() {
    // Given
    llm.stubFor(post(urlPathEqualTo("/v1/chat/completions")).willReturn(aResponse().withStatus(401)
        .withHeader("Content-Type", "application/json").withBody("{\"error\":{\"message\":\"Invalid API key\"}}")));

    // When
    ApiClient.Result chat = admin.post(API + "/projects/" + projectId + "/ai/chat", "{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");

    // Then
    assertThat(chat.status()).isEqualTo(502);
    assertThat(chat.json().get("error").asText()).contains("Invalid API key");
  }
}
