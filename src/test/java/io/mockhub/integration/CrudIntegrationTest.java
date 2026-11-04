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
  void filtersWithOperatorsSortsByManyFieldsAndPagesBySize() {
    // Given
    api.post(API + "/projects/" + projectId + "/crud",
        "{\"resource\":\"products\",\"idType\":\"number\",\"seed\":["
            + "{\"name\":\"Pen\",\"price\":2.5,\"brand\":{\"country\":\"UK\"}},"
            + "{\"name\":\"Desk\",\"price\":180,\"brand\":{\"country\":\"SE\"}},"
            + "{\"name\":\"Lamp\",\"price\":35,\"brand\":{\"country\":\"UK\"}},"
            + "{\"name\":\"Chair\",\"price\":95,\"brand\":{\"country\":\"DE\"}}]}");

    // When
    JsonNode range = api.get("/shop/products?price%5Bgte%5D=35&price%5Blt%5D=180&sort=-price").json();
    JsonNode either = api.get("/shop/products?brand.country=SE&brand.country=DE&sort=name").json();
    ApiClient.Result second = api.get("/shop/products?sort=price&page=2&size=3");
    ApiClient.Result unknown = api.get("/shop/products?price%5Bbetween%5D=1");

    // Then
    assertThat(range.findValuesAsText("name")).containsExactly("Chair", "Lamp");
    assertThat(either.findValuesAsText("name")).containsExactly("Chair", "Desk");
    assertThat(second.json().findValuesAsText("name")).containsExactly("Desk");
    assertThat(second.header("X-Total-Count")).isEqualTo("4");
    assertThat(unknown.status()).isEqualTo(400);
    assertThat(unknown.json().get("error").asText()).contains("price[between]");
  }

  @Test
  void explainsBadRequests() {
    // Given
    customers();

    // Then
    ApiClient.Result notJson = api.post("/shop/customers", "{\"name\": Ada}");
    assertThat(notJson.status()).isEqualTo(400);
    assertThat(notJson.json().get("code").asText()).isEqualTo("invalid_json_body");
    assertThat(notJson.json().get("error").asText()).isEqualTo("The body isn't valid JSON (line 1, column 10)");
    assertThat(api.post("/shop/customers", "[1,2]").json().get("code").asText()).isEqualTo("invalid_body");
    ApiClient.Result dupe = api.post("/shop/customers", "{\"id\":1,\"name\":\"Dupe\"}");
    assertThat(dupe.status()).isEqualTo(409);
    assertThat(dupe.json().get("code").asText()).isEqualTo("already_exists");
    assertThat(api.delete("/shop/customers").json().get("code").asText()).isEqualTo("method_not_allowed");
    assertThat(api.get("/shop/customers?page=0").json().get("code").asText()).isEqualTo("invalid_query");
    for (ApiClient.Result missing : new ApiClient.Result[] {api.put("/shop/customers/99", "{}"), api.get("/shop/customers/99"), api.delete("/shop/customers/99")}) {
      assertThat(missing.status()).isEqualTo(404);
      assertThat(missing.json().get("code").asText()).isEqualTo("not_found");
      assertThat(missing.json().get("error").asText()).isEqualTo("No customers record has id 99");
      assertThat(missing.json().get("resource").asText()).isEqualTo("customers");
      assertThat(missing.json().get("id").asText()).isEqualTo("99");
    }
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

  @Test
  void aResourceKeepsItsNewestRecordsUpToTheConfiguredLimit() {
    // Given
    server.close();
    server = MockHubServer.start(data, Map.of("MOCKHUB_MAX_RECORDS", "3"));
    api = server.api();
    customers();

    // When
    for (String name : new String[] {"Grace", "Edsger", "Barbara"}) api.post("/shop/customers", "{\"name\":\"" + name + "\"}");

    // Then
    JsonNode left = api.get("/shop/customers").json();
    assertThat(left.findValuesAsText("name")).containsExactly("Grace", "Edsger", "Barbara");
  }
}
