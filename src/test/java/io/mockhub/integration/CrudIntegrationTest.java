package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrudIntegrationTest {

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
    projectId = api.post(API + "/projects", "{\"name\":\"Shop\",\"slug\":\"shop\"}").json().get("id").asText();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private void customers() {
    ApiClient.Result created = api.post(API + "/projects/" + projectId + "/crud",
        "{\"resource\":\"customers\",\"idType\":\"number\",\"seed\":[{\"name\":\"Ada\",\"city\":\"London\"},{\"name\":\"Alan\",\"city\":\"Wilmslow\"}]}");
    assertThat(created.status()).as(created.body()).isEqualTo(201);
    assertThat(created.json().get("seeded").asInt()).isEqualTo(2);
  }

  @Test
  void aCrudResourceStoresAndServesRecords() {
    // Given
    customers();

    // When
    ApiClient.Result created = api.post("/shop/customers", "{\"name\":\"Grace\",\"city\":\"New York\"}");
    ApiClient.Result patched = api.send("PATCH", "/shop/customers/3", "{\"city\":\"Arlington\"}", "application/json");
    ApiClient.Result replaced = api.put("/shop/customers/2", "{\"name\":\"Alan Turing\"}");
    ApiClient.Result deleted = api.delete("/shop/customers/1");

    // Then
    assertThat(created.status()).isEqualTo(201);
    assertThat(created.json().get("id").asInt()).isEqualTo(3);
    assertThat(created.location()).isEqualTo("/shop/customers/3");
    assertThat(patched.json().get("city").asText()).isEqualTo("Arlington");
    assertThat(patched.json().get("name").asText()).isEqualTo("Grace");
    assertThat(replaced.json().get("id").asInt()).isEqualTo(2);
    assertThat(replaced.json().has("city")).isFalse();
    assertThat(deleted.status()).isEqualTo(204);
    assertThat(api.get("/shop/customers/1").status()).isEqualTo(404);
    JsonNode all = api.get("/shop/customers").json();
    assertThat(all.findValuesAsText("name")).containsExactly("Alan Turing", "Grace");
  }

  @Test
  void listsFilterSortAndPage() {
    // Given
    customers();
    api.post("/shop/customers", "{\"name\":\"Grace\",\"city\":\"London\"}");

    // When
    JsonNode london = api.get("/shop/customers?city=London").json();
    JsonNode sorted = api.get("/shop/customers?_sort=name&_order=desc").json();
    JsonNode page = api.get("/shop/customers?_sort=id&_limit=1&_offset=1").json();

    // Then
    assertThat(london.findValuesAsText("name")).containsExactly("Ada", "Grace");
    assertThat(sorted.findValuesAsText("name")).containsExactly("Grace", "Alan", "Ada");
    assertThat(page.findValuesAsText("name")).containsExactly("Alan");
  }

  @Test
  void explainsBadRequests() {
    // Given
    customers();

    // Then
    assertThat(api.post("/shop/customers", "not json").status()).isEqualTo(400);
    assertThat(api.post("/shop/customers", "[1,2]").status()).isEqualTo(400);
    assertThat(api.post("/shop/customers", "{\"id\":1,\"name\":\"Dupe\"}").status()).isEqualTo(409);
    assertThat(api.delete("/shop/customers").status()).isEqualTo(405);
    assertThat(api.put("/shop/customers/99", "{}").status()).isEqualTo(404);
    assertThat(api.post(API + "/projects/" + projectId + "/crud", "{\"resource\":\"customers\"}").status()).isEqualTo(409);
    assertThat(api.post(API + "/projects/" + projectId + "/crud", "{\"resource\":\"Bad Name\"}").status()).isEqualTo(400);
  }

  @Test
  void theDataApiShowsAndEditsRecords() {
    // Given
    customers();

    // When
    JsonNode resources = api.get(API + "/projects/" + projectId + "/data").json().get("resources");
    ApiClient.Result added = api.post(API + "/projects/" + projectId + "/data/records?resource=customers", "{\"name\":\"Hedy\"}");
    ApiClient.Result removed = api.delete(API + "/projects/" + projectId + "/data/records?resource=customers&id=1");
    JsonNode records = api.get(API + "/projects/" + projectId + "/data/records?resource=customers").json();
    ApiClient.Result cleared = api.delete(API + "/projects/" + projectId + "/data/records?resource=customers");

    // Then
    assertThat(resources.get(0).get("name").asText()).isEqualTo("customers");
    assertThat(resources.get(0).get("count").asInt()).isEqualTo(2);
    assertThat(resources.get(0).get("idType").asText()).isEqualTo("number");
    assertThat(added.json().get("id").asInt()).isEqualTo(3);
    assertThat(removed.status()).isEqualTo(204);
    assertThat(records.findValuesAsText("name")).containsExactly("Alan", "Hedy");
    assertThat(cleared.status()).isEqualTo(204);
    assertThat(api.get("/shop/customers").json()).isEmpty();
  }

  @Test
  void recordsSurviveARestartAndTravelInBackups(@TempDir Path other) {
    // Given
    customers();
    server.close();
    server = MockHubServer.start(data);
    api = server.api();

    // When
    JsonNode afterRestart = api.get("/shop/customers").json();
    String backup = api.get(API + "/backup").body();

    // Then
    assertThat(afterRestart.findValuesAsText("name")).containsExactly("Ada", "Alan");
    try (MockHubServer fresh = MockHubServer.start(other)) {
      fresh.api().post(API + "/restore", backup);
      assertThat(fresh.api().get("/shop/customers/2").json().get("name").asText()).isEqualTo("Alan");
    }
  }

  @Test
  void deletingTheProjectDeletesItsData() {
    // Given
    customers();

    // When
    api.delete(API + "/projects/" + projectId);

    // Then
    assertThat(data.resolve("mockhub/data/" + projectId + ".json")).doesNotExist();
  }

  @Test
  void theStateExtensionIsAvailableForCustomFlows() {
    // Given
    api.post(API + "/projects/" + projectId + "/stubs", """
        {"request":{"method":"POST","urlPath":"/visits"},
         "response":{"status":200,"body":"recorded"},
         "serveEventListeners":[{"name":"recordState","parameters":{"context":"visits","state":{"last":"{{jsonPath request.body '$.page'}}"}}}]}""");
    api.post(API + "/projects/" + projectId + "/stubs", """
        {"request":{"method":"GET","urlPath":"/visits/last"},
         "response":{"status":200,"body":"{{state context='visits' property='last'}}","transformers":["response-template"]}}""");

    // When
    api.post("/shop/visits", "{\"page\":\"/checkout\"}");

    // Then
    assertThat(api.get("/shop/visits/last").body()).isEqualTo("/checkout");
  }
}
