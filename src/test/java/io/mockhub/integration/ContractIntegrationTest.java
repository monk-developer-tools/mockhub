package io.mockhub.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.mockhub.api.Jsons;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContractIntegrationTest {

  private static final String API = "/__admin/mockhub/api";
  private static final String CONTRACT = """
      openapi: 3.0.3
      info: { title: Orders API, version: "2.0" }
      components:
        schemas:
          Order:
            type: object
            required: [id, total]
            properties:
              id: { type: integer }
              total: { type: number }
      paths:
        /orders/{id}:
          get:
            parameters:
              - { name: id, in: path, required: true, schema: { type: integer } }
            responses:
              '200':
                description: ok
                content:
                  application/json:
                    schema: { $ref: '#/components/schemas/Order' }
      """;

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient admin;
  private WireMockServer outside;
  private String projectId;

  @BeforeEach
  void start() {
    outside = new WireMockServer(options().dynamicPort());
    outside.start();
    server = MockHubServer.start(data, Map.of("MOCKHUB_USERS", "root:pw:admin,qa:pw:viewer", "MOCKHUB_CALLOUT_ALLOW_PRIVATE", "true"));
    admin = server.api().login("root", "pw");
    projectId = admin.post(API + "/projects", "{\"name\":\"Orders\",\"slug\":\"orders\"}").json().get("id").asText();
  }

  @AfterEach
  void stop() {
    server.close();
    outside.stop();
  }

  private String base() {
    return API + "/projects/" + projectId;
  }

  private void attach(String yaml) {
    ApiClient.Result r = admin.put(base() + "/contract", Jsons.write(Jsons.object().put("content", yaml).put("source", "upload").put("fileName", "orders.yaml")));
    assertThat(r.status()).as(r.body()).isEqualTo(200);
  }

  private void stub(String json) {
    ApiClient.Result r = admin.post(base() + "/stubs", json);
    assertThat(r.status()).as(r.body()).isEqualTo(201);
  }

  private JsonNode check() {
    ApiClient.Result r = admin.post(base() + "/drift/check", "");
    assertThat(r.status()).as(r.body()).isEqualTo(200);
    return r.json();
  }

  private static List<String> kinds(JsonNode drift) {
    List<String> out = new ArrayList<>();
    drift.get("findings").forEach(f -> out.add(f.get("kind").asText() + " " + f.get("method").asText() + " " + f.get("path").asText()
        + (f.get("detail").asText().isEmpty() ? "" : " | " + f.get("detail").asText())));
    return out;
  }

  @Test
  void trafficThatDisagreesWithTheContractShowsAsDrift() {
    // Given
    attach(CONTRACT);
    stub("{\"request\":{\"method\":\"GET\",\"urlPathPattern\":\"/orders/[0-9]+\"},\"response\":{\"status\":200,"
        + "\"headers\":{\"Content-Type\":\"application/json\"},\"jsonBody\":{\"id\":7,\"total\":\"12.50\"}}}");
    stub("{\"request\":{\"method\":\"GET\",\"urlPath\":\"/refunds\"},\"response\":{\"status\":200,\"jsonBody\":[]}}");
    admin.get("/orders/orders/7");
    admin.get("/orders/orders/8");
    admin.get("/orders/refunds");

    // When
    JsonNode drift = check();

    // Then
    assertThat(kinds(drift)).containsExactlyInAnyOrder(
        "RESPONSE_BODY_SCHEMA GET /orders/{id} | 200 $.total: string found, number expected",
        "UNDOCUMENTED_PATH GET /refunds");
    JsonNode schema = find(drift, "RESPONSE_BODY_SCHEMA");
    assertThat(schema.get("count").asInt()).isEqualTo(2);
    assertThat(schema.get("label").asText()).isEqualTo("Response body doesn't match its schema");
    assertThat(schema.at("/example/url").asText()).startsWith("/orders/orders/");
    assertThat(drift.get("scanned").asInt()).isEqualTo(3);
    assertThat(drift.get("open").asInt()).isEqualTo(2);
  }

  @Test
  void aFindingCanBeMarkedAsExpected() {
    // Given
    attach(CONTRACT);
    admin.get("/orders/refunds");
    String key = check().get("findings").get(0).get("key").asText();

    // When
    ApiClient.Result marked = admin.put(base() + "/drift/findings/" + key, "{\"expected\":true}");

    // Then
    assertThat(marked.status()).isEqualTo(200);
    assertThat(marked.json().get("open").asInt()).isZero();
    assertThat(marked.json().get("expected").asInt()).isEqualTo(1);
    assertThat(admin.get(base() + "/drift").json().at("/findings/0/expected").asBoolean()).isTrue();
  }

  @Test
  void importingOpenApiCanKeepItAsTheContract() {
    // Given
    String spec = Jsons.write(io.mockhub.contract.ContractDocument.parse(CONTRACT));

    // When
    ApiClient.Result imported = admin.post(base() + "/import/openapi?contract=true&fileName=orders.json", spec);

    // Then
    assertThat(imported.status()).as(imported.body()).isEqualTo(200);
    assertThat(imported.json().get("contract").asBoolean()).isTrue();
    JsonNode contract = admin.get(base() + "/contract").json();
    assertThat(contract.get("attached").asBoolean()).isTrue();
    assertThat(contract.get("title").asText()).isEqualTo("Orders API");
    assertThat(contract.get("source").asText()).isEqualTo("import");
    assertThat(contract.get("operations").get(0).get("path").asText()).isEqualTo("/orders/{id}");
    admin.get("/orders/orders/3");
    assertThat(check().get("findings")).isEmpty();
    ApiClient.Result download = admin.get(base() + "/contract/spec?format=yaml");
    assertThat(download.contentDisposition()).contains("orders-openapi.yaml");
    assertThat(download.body()).contains("title: \"Orders API\"");
  }

  @Test
  void anImportThatStripsAPrefixMatchesTrafficWithoutIt() {
    // Given
    String spec = Jsons.write(io.mockhub.contract.ContractDocument.parse(CONTRACT.replace("/orders/{id}:", "/api/orders/{id}:")));

    // When
    ApiClient.Result imported = admin.post(base() + "/import/openapi?contract=true&stripPrefix=/api", spec);
    admin.get("/orders/orders/4");

    // Then
    assertThat(imported.status()).as(imported.body()).isEqualTo(200);
    assertThat(admin.get(base() + "/contract").json().get("stripPrefix").asText()).isEqualTo("/api");
    assertThat(check().get("findings")).isEmpty();
  }

  @Test
  void newDriftIsSentToTheNotificationWebhook() {
    // Given
    attach(CONTRACT);
    outside.stubFor(post(urlEqualTo("/drift-hook")).willReturn(aResponse().withStatus(204)));
    ApiClient.Result saved = admin.put(base() + "/contract/notify", "{\"url\":\"http://localhost:" + outside.port() + "/drift-hook\"}");
    assertThat(saved.status()).as(saved.body()).isEqualTo(200);
    admin.get("/orders/refunds");

    // When
    JsonNode drift = check();

    // Then
    outside.verify(postRequestedFor(urlEqualTo("/drift-hook"))
        .withRequestBody(matchingJsonPath("$.event", com.github.tomakehurst.wiremock.client.WireMock.equalTo("contract.drift")))
        .withRequestBody(matchingJsonPath("$.findings[0].path", com.github.tomakehurst.wiremock.client.WireMock.equalTo("/refunds"))));
    assertThat(drift.at("/lastNotice/status").asInt()).isEqualTo(204);
    check();
    outside.verify(1, postRequestedFor(urlEqualTo("/drift-hook")));
  }

  @Test
  void theAssistantSuggestsAPatchThatResolvesTheDrift() {
    // Given
    attach(CONTRACT);
    admin.get("/orders/refunds");
    check();
    ApiClient.Result ai = admin.put(API + "/ai/settings", "{\"enabled\":true,\"provider\":\"openai\",\"baseUrl\":\"http://localhost:" + outside.port()
        + "/v1\",\"apiKey\":\"sk-test\",\"model\":\"test-model\"}");
    assertThat(ai.status()).as(ai.body()).isEqualTo(200);
    String patch = "{\"summary\":\"Adds refunds\",\"patch\":[{\"op\":\"add\",\"path\":\"/paths/~1refunds\",\"value\":{\"get\":{\"responses\":"
        + "{\"200\":{\"description\":\"ok\",\"content\":{\"application/json\":{\"schema\":{\"type\":\"array\"}}}}}}}}]}";
    outside.stubFor(post(urlPathEqualTo("/v1/chat/completions")).willReturn(aResponse().withHeader("Content-Type", "application/json")
        .withBody(Jsons.write(Jsons.object().set("choices", Jsons.MAPPER.createArrayNode().add(Jsons.object()
            .set("message", Jsons.object().put("role", "assistant").put("content", patch))))))));

    // When
    ApiClient.Result suggested = admin.post(base() + "/drift/patch", "{}");
    assertThat(suggested.status()).as(suggested.body()).isEqualTo(200);
    ApiClient.Result applied = admin.put(base() + "/contract",
        Jsons.write(Jsons.object().put("content", suggested.json().get("after").asText()).put("source", "ai-patch")));
    JsonNode drift = admin.post(base() + "/drift/recheck", "").json();

    // Then
    assertThat(suggested.json().get("summary").asText()).isEqualTo("Adds refunds");
    assertThat(suggested.json().get("before").asText()).doesNotContain("/refunds");
    assertThat(applied.status()).isEqualTo(200);
    assertThat(applied.json().get("source").asText()).isEqualTo("ai-patch");
    assertThat(drift.get("findings")).isEmpty();
    assertThat(drift.get("scanned").asInt()).isEqualTo(1);
  }

  @Test
  void viewersCanCheckButNotChangeTheContract() {
    // Given
    attach(CONTRACT);
    ApiClient qa = server.api().login("qa", "pw");

    // When / Then
    assertThat(qa.get(base() + "/contract").status()).isEqualTo(200);
    assertThat(qa.post(base() + "/drift/check", "").status()).isEqualTo(200);
    assertThat(qa.put(base() + "/contract", "{\"content\":\"{}\"}").status()).isEqualTo(403);
    assertThat(qa.post(base() + "/drift/patch", "{}").status()).isEqualTo(403);
    assertThat(qa.delete(base() + "/contract").status()).isEqualTo(403);
  }

  @Test
  void documentsThatAreNotOpenApiAreRefused() {
    // Given / When
    ApiClient.Result r = admin.put(base() + "/contract", "{\"content\":\"{\\\"hello\\\":1}\"}");

    // Then
    assertThat(r.status()).isEqualTo(400);
    assertThat(r.body()).contains("Not an OpenAPI 3 or Swagger 2 document");
    assertThat(admin.post(base() + "/drift/check", "").status()).isEqualTo(404);
  }

  private static JsonNode find(JsonNode drift, String kind) {
    for (JsonNode f : drift.get("findings")) if (f.get("kind").asText().equals(kind)) return f;
    return null;
  }
}
