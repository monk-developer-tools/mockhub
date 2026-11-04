package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicToolsIntegrationTest {

  private static final Map<String, String> ON = Map.of("MOCKHUB_PUBLIC_TOOLS", "true");

  @TempDir
  Path data;
  private MockHubServer server;

  @AfterEach
  void stop() {
    if (server != null) server.close();
  }

  @Test
  void staysOffUnlessTurnedOn() {
    // Given
    server = MockHubServer.start(data);

    // When
    ApiClient.Result result = server.api().get("/__public/echo");

    // Then
    assertThat(result.status()).isEqualTo(404);
    assertThat(result.body()).doesNotContain("\"method\"");
  }

  @Test
  void echoesRequestsWithoutLogin() {
    // Given
    server = MockHubServer.start(data, ON);

    // When
    ApiClient.Result result = server.api().send("PUT", "/__public/echo/things?x=1", "{\"n\":2}", "application/json", Map.of("X-Demo", "yes"));

    // Then
    assertThat(result.status()).isEqualTo(200);
    JsonNode body = result.json();
    assertThat(body.get("method").asText()).isEqualTo("PUT");
    assertThat(body.get("path").asText()).isEqualTo("/things");
    assertThat(body.at("/headers/X-Demo").asText()).isEqualTo("yes");
    assertThat(body.at("/json/n").asInt()).isEqualTo(2);
    assertThat(result.header("Access-Control-Allow-Origin")).isEqualTo("*");
  }

  @Test
  void catchesWebhooksInABinAndShowsThemToItsOwner() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    JsonNode bin = api.post("/__public/bins", "").json();
    String id = bin.get("id").asText();
    String token = bin.get("token").asText();
    api.send("PUT", "/__public/inspect/" + id + "/response", "{\"status\":202,\"contentType\":\"text/plain\",\"body\":\"got it\"}",
        "application/json", Map.of("X-Bin-Token", token));

    // When
    ApiClient.Result hook = api.post("/__public/bins/" + id + "/payments?ref=9", "{\"paid\":true}");
    ApiClient.Result owner = api.send("GET", "/__public/inspect/" + id, null, null, Map.of("X-Bin-Token", token));
    ApiClient.Result stranger = api.get("/__public/inspect/" + id);

    // Then
    assertThat(hook.status()).isEqualTo(202);
    assertThat(hook.body()).isEqualTo("got it");
    JsonNode captured = owner.json().at("/requests/0");
    assertThat(captured.get("method").asText()).isEqualTo("POST");
    assertThat(captured.get("path").asText()).isEqualTo("/payments");
    assertThat(captured.get("query").asText()).isEqualTo("ref=9");
    assertThat(captured.get("body").asText()).isEqualTo("{\"paid\":true}");
    assertThat(stranger.status()).isEqualTo(404);
  }

  @Test
  void rendersTemplatesAgainstASampleRequest() {
    // Given
    server = MockHubServer.start(data, ON);
    String body = "{\"template\":\"Hi {{request.query.name}}, {{jsonPath request.body '$.n'}} {{fake 'Name.firstName'}}\","
        + "\"request\":{\"method\":\"POST\",\"url\":\"/x?name=Ada\",\"body\":{\"n\":3}}}";

    // When
    ApiClient.Result ok = server.api().post("/__public/render", body);
    ApiClient.Result refused = server.api().post("/__public/render", "{\"template\":\"{{systemValue key='HOME'}}\"}");

    // Then
    assertThat(ok.status()).isEqualTo(200);
    assertThat(ok.json().get("output").asText()).startsWith("Hi Ada, 3 ").hasSizeGreaterThan("Hi Ada, 3 ".length());
    assertThat(refused.status()).isEqualTo(422);
    assertThat(refused.json().get("error").asText()).contains("turned off");
  }

  @Test
  void generatesFakeData() {
    // Given
    server = MockHubServer.start(data, ON);

    // When
    ApiClient.Result result = server.api().post("/__public/fake",
        "{\"count\":2,\"fields\":[{\"name\":\"id\",\"expression\":\"uuid\"},{\"name\":\"user.email\",\"expression\":\"Internet.emailAddress\"}]}");

    // Then
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.json().get("records")).hasSize(2);
    assertThat(result.json().at("/records/0/user/email").asText()).contains("@");
  }

  @Test
  void answersBrowserPreflights() {
    // Given
    server = MockHubServer.start(data, ON);

    // When
    ApiClient.Result result = server.api().send("OPTIONS", "/__public/render", null, null,
        Map.of("Origin", "https://example.com", "Access-Control-Request-Method", "POST"));

    // Then
    assertThat(result.status()).isEqualTo(204);
    assertThat(result.header("Access-Control-Allow-Methods")).contains("POST");
  }

  @Test
  void makesAnInstantMockWithoutAnAccountThatAnswersRightAway() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    JsonNode created = api.post("/__public/mocks", "").json();
    String base = created.get("baseUrl").asText();
    Map<String, String> key = Map.of("X-Mock-Token", created.get("token").asText());

    // When
    ApiClient.Result hello = api.get(base + "/hello");
    ApiClient.Result user = api.get(base + "/users/7");
    ApiClient.Result added = api.send("POST", "/__public/mocks/" + created.get("slug").asText() + "/stubs",
        "{\"name\":\"Pay\",\"request\":{\"method\":\"POST\",\"urlPath\":\"/payments\"},\"response\":{\"status\":201,\"jsonBody\":{\"id\":\"p1\"}}}",
        "application/json", key);
    ApiClient.Result paid = api.post(base + "/payments", "{}");
    JsonNode mock = api.send("GET", "/__public/mocks/" + created.get("slug").asText(), null, null, key).json();
    JsonNode requests = api.send("GET", "/__public/mocks/" + created.get("slug").asText() + "/requests", null, null, key).json();

    // Then
    assertThat(base).matches("/m-[a-z0-9]{10}");
    assertThat(hello.json().get("message").asText()).isEqualTo("Hello from your instant mock");
    assertThat(user.json().get("id").asText()).isEqualTo("7");
    assertThat(user.json().get("email").asText()).contains("@");
    assertThat(added.status()).isEqualTo(201);
    assertThat(paid.status()).isEqualTo(201);
    assertThat(mock.get("stubs")).hasSize(3);
    assertThat(mock.get("stubs").findValuesAsText("name")).containsExactlyInAnyOrder("Say hello", "Get a user", "Pay");
    assertThat(mock.toString()).contains("\"response-template\"").doesNotContain("mockhub-public-template");
    assertThat(requests.get("requests").size()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void managesAnInstantMockOnlyWithItsKey() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    JsonNode created = api.post("/__public/mocks", "").json();
    String path = "/__public/mocks/" + created.get("slug").asText();

    // When
    ApiClient.Result noKey = api.get(path);
    ApiClient.Result wrongKey = api.send("DELETE", path, null, null, Map.of("X-Mock-Token", "guess"));
    ApiClient.Result proxy = api.send("POST", path + "/stubs",
        "{\"request\":{\"url\":\"/x\"},\"response\":{\"proxyBaseUrl\":\"http://169.254.169.254\"}}", "application/json",
        Map.of("X-Mock-Token", created.get("token").asText()));

    // Then
    assertThat(noKey.status()).isEqualTo(404);
    assertThat(wrongKey.status()).isEqualTo(404);
    assertThat(proxy.status()).isEqualTo(400);
    assertThat(proxy.json().get("error").asText()).contains("proxyBaseUrl");
  }

  @Test
  void rendersInstantMockTemplatesInsideTheSandbox() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    JsonNode created = api.post("/__public/mocks", "{\"stubs\":[{\"request\":{\"urlPath\":\"/bomb\"},\"response\":{\"status\":200,"
        + "\"body\":\"{{#each (range 1 100)}}{{#each (range 1 100)}}x{{/each}}{{/each}}{{systemValue key='user.home'}}\","
        + "\"transformers\":[\"response-template\"]}}]}").json();

    // When
    ApiClient.Result bomb = api.get(created.get("baseUrl").asText() + "/bomb");

    // Then
    assertThat(bomb.status()).isEqualTo(500);
    assertThat(bomb.json().get("error").asText()).contains("Loops in the public playground run up to");
    awaitHistory(api, created);
  }

  private static void awaitHistory(ApiClient api, JsonNode created) {
    Map<String, String> key = Map.of("X-Mock-Token", created.get("token").asText());
    String path = "/__public/mocks/" + created.get("slug").asText() + "/requests";
    long deadline = System.currentTimeMillis() + 5_000;
    while (api.send("GET", path, null, null, key).json().get("requests").isEmpty() && System.currentTimeMillis() < deadline) {
      Thread.onSpinWait();
    }
  }

  @Test
  void hidesInstantMocksFromServerViewersAndTheWireMockAdminApi() {
    // Given
    server = MockHubServer.start(data, Map.of("MOCKHUB_PUBLIC_TOOLS", "true", "MOCKHUB_USERS", "admin:pw:admin,demo:demo:viewer"));
    ApiClient api = server.api();
    JsonNode created = api.post("/__public/mocks", "").json();
    ApiClient viewer = server.api().as("demo", "demo");
    ApiClient admin = server.api().as("admin", "pw");

    // When
    JsonNode seenByViewer = viewer.get("/__admin/mockhub/api/projects").json();
    JsonNode seenByAdmin = admin.get("/__admin/mockhub/api/projects").json();
    ApiClient.Result nativeApi = viewer.get("/__admin/mappings");

    // Then
    assertThat(seenByViewer.findValuesAsText("slug")).doesNotContain(created.get("slug").asText());
    assertThat(seenByAdmin.findValuesAsText("slug")).contains(created.get("slug").asText());
    assertThat(nativeApi.status()).isEqualTo(403);
  }
}
