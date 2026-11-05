package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiLinkIntegrationTest {

  private static final String API = "/__admin/mockhub/api";
  private static final String SPEC = "openapi: 3.0.0\\ninfo:\\n  title: Pets\\n  version: '2.1'\\npaths:\\n  /pets:\\n    get:\\n      responses:\\n        '200':\\n          description: ok\\n    post:\\n      responses:\\n        '201':\\n          description: made\\n";

  @TempDir
  Path data;
  private MockHubServer server;

  @AfterEach
  void stop() {
    if (server != null) server.close();
  }

  private ApiClient serveSpec(Map<String, String> env) {
    server = MockHubServer.start(data, env);
    ApiClient api = server.api();
    String id = api.post(API + "/projects", "{\"name\":\"Specs\",\"slug\":\"specs\"}").json().get("id").asText();
    api.post(API + "/projects/" + id + "/stubs", "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/pets.yaml\"},\"response\":{\"status\":200,\"body\":\"" + SPEC + "\"}}");
    api.post(API + "/projects/" + id + "/stubs", "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/latest\"},\"response\":{\"status\":302,\"headers\":{\"Location\":\"/specs/pets.yaml\"}}}");
    api.post(API + "/projects/" + id + "/stubs", "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/readme\"},\"response\":{\"status\":200,\"body\":\"hello\"}}");
    return api;
  }

  @Test
  void downloadsAnOpenApiFileAndSummarisesIt() {
    // Given
    ApiClient api = serveSpec(Map.of("MOCKHUB_PLAYGROUND_ALLOW_PRIVATE", "true"));

    // When
    ApiClient.Result r = api.post(API + "/openapi/fetch", "{\"url\":\"" + server.baseUrl() + "/specs/latest\"}");

    // Then
    assertThat(r.status()).as(r.body()).isEqualTo(200);
    assertThat(r.json().get("title").asText()).isEqualTo("Pets");
    assertThat(r.json().get("version").asText()).isEqualTo("2.1");
    assertThat(r.json().get("operations").asInt()).isEqualTo(2);
    assertThat(r.json().get("content").asText()).startsWith("openapi: 3.0.0");
  }

  @Test
  void refusesFilesThatAreNotOpenApi() {
    // Given
    ApiClient api = serveSpec(Map.of("MOCKHUB_PLAYGROUND_ALLOW_PRIVATE", "true"));

    // When
    ApiClient.Result r = api.post(API + "/openapi/fetch", "{\"url\":\"" + server.baseUrl() + "/specs/readme\"}");

    // Then
    assertThat(r.status()).isEqualTo(400);
  }

  @Test
  void refusesLocalAddressesUnlessAnAdminAllowsThem() {
    // Given
    ApiClient api = serveSpec(Map.of());

    // When
    ApiClient.Result r = api.post(API + "/openapi/fetch", "{\"url\":\"" + server.baseUrl() + "/specs/pets.yaml\"}");

    // Then
    assertThat(r.status()).isEqualTo(400);
    assertThat(r.body()).contains("MOCKHUB_PLAYGROUND_ALLOW_PRIVATE");
  }
}
