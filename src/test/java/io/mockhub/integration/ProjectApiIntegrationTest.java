package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectApiIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient api;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data);
    api = server.api();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  @Test
  void createsAProjectWithAMockUrl() {
    // When
    ApiClient.Result created = api.post(API + "/projects", "{\"name\":\"Payment Service\"}");

    // Then
    assertThat(created.status()).isEqualTo(201);
    assertThat(created.json().get("slug").asText()).isEqualTo("payment-service");
    assertThat(created.json().get("mockUrl").asText()).isEqualTo(server.baseUrl() + "/payment-service");
    assertThat(api.get(API + "/projects").json()).hasSize(1);
  }

  @Test
  void servesAProjectStubUnderItsBasePathWithTemplating() {
    // Given
    String id = api.post(API + "/projects", "{\"name\":\"Payments\"}").json().get("id").asText();
    api.post(API + "/projects/" + id + "/stubs",
        "{\"request\":{\"method\":\"GET\",\"urlPathTemplate\":\"/payments/{id}\"},"
            + "\"response\":{\"status\":200,\"jsonBody\":{\"id\":\"{{request.path.id}}\"},\"transformers\":[\"response-template\"]}}");

    // When
    ApiClient.Result mock = api.get("/payments/payments/42");

    // Then
    assertThat(mock.status()).isEqualTo(200);
    assertThat(mock.json().get("id").asText()).isEqualTo("42");
    assertThat(api.get(API + "/projects/" + id).json().get("requestCount").asInt()).isEqualTo(1);
  }

  @Test
  void returnsStubsRelativeToTheProject() {
    // Given
    String id = api.post(API + "/projects", "{\"name\":\"Orders\"}").json().get("id").asText();
    api.post(API + "/projects/" + id + "/stubs", "{\"request\":{\"method\":\"GET\",\"url\":\"/items\"}}");

    // When
    ApiClient.Result stubs = api.get(API + "/projects/" + id + "/stubs");

    // Then
    assertThat(stubs.json().get(0).at("/request/url").asText()).isEqualTo("/items");
    assertThat(api.get("/__admin/mappings").json().at("/mappings/0/request/url").asText()).isEqualTo("/orders/items");
  }

  @Test
  void renamingTheBasePathMovesTheMocks() {
    // Given
    String id = api.post(API + "/projects", "{\"name\":\"Orders\"}").json().get("id").asText();
    api.post(API + "/projects/" + id + "/stubs", "{\"request\":{\"method\":\"GET\",\"url\":\"/items\"},\"response\":{\"status\":204}}");

    // When
    api.put(API + "/projects/" + id, "{\"slug\":\"orders-v2\"}");

    // Then
    assertThat(api.get("/orders-v2/items").status()).isEqualTo(204);
    assertThat(api.get("/orders/items").status()).isEqualTo(404);
  }

  @Test
  void deletingAProjectRemovesItsMocks() {
    // Given
    String id = api.post(API + "/projects", "{\"name\":\"Orders\"}").json().get("id").asText();
    api.post(API + "/projects/" + id + "/stubs", "{\"request\":{\"method\":\"GET\",\"url\":\"/items\"}}");

    // When
    ApiClient.Result deleted = api.delete(API + "/projects/" + id);

    // Then
    assertThat(deleted.status()).isEqualTo(204);
    assertThat(api.get("/__admin/mappings").json().get("mappings")).isEmpty();
  }

  @Test
  void filtersTheRequestLogByProject() {
    // Given
    String a = api.post(API + "/projects", "{\"name\":\"A\"}").json().get("id").asText();
    api.post(API + "/projects", "{\"name\":\"B\"}");
    api.get("/a/one");
    api.get("/b/two");

    // When
    ApiClient.Result log = api.get(API + "/projects/" + a + "/requests?unmatched=true");

    // Then
    assertThat(log.json()).singleElement().satisfies(e -> assertThat(e.at("/request/url").asText()).isEqualTo("/a/one"));
  }

  @Test
  void reportsValidationErrorsAsJson() {
    assertThat(api.post(API + "/projects", "{}").status()).isEqualTo(400);
    assertThat(api.post(API + "/projects", "{bad").json().get("error").asText()).contains("Invalid JSON");
    assertThat(api.get(API + "/projects/missing").status()).isEqualTo(404);
    assertThat(api.get(API + "/nope").status()).isEqualTo(404);
    assertThat(api.put(API + "/projects", "{}").status()).isEqualTo(405);
  }

  @Test
  void stubsCreatedThroughTheNativeAdminApiArePersisted() {
    // When
    api.post("/__admin/mappings", "{\"request\":{\"url\":\"/raw\"},\"response\":{\"status\":200}}");

    // Then
    assertThat(data.resolve("mappings").toFile().list()).hasSize(1);
    assertThat(api.get(API + "/stubs/unassigned").json()).hasSize(1);
  }
}
