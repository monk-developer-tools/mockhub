package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StateIntegrationTest {

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
    api.post(API + "/projects/" + projectId + "/stubs", """
        {"request":{"method":"POST","urlPathTemplate":"/carts/{id}/items"},
         "response":{"status":201,"body":"added"},
         "serveEventListeners":[{"name":"recordState","parameters":{"context":"cart-{{request.path.id}}",
           "state":{"status":"open"},"list":{"addLast":{"sku":"{{jsonPath request.body '$.sku'}}"}}}}]}""");
    api.post(API + "/projects/" + projectId + "/stubs", """
        {"request":{"method":"GET","urlPathTemplate":"/carts/{id}"},
         "response":{"status":200,"transformers":["response-template"],
           "body":"{{state context=(join 'cart-' request.path.id '') property='status' default='none'}} {{state context=(join 'cart-' request.path.id '') property='listSize' default='0'}}"}}""");
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private JsonNode contexts() {
    return api.get(API + "/projects/" + projectId + "/state").json().get("contexts");
  }

  @Test
  void stateWrittenByAProjectsStubsIsListedForThatProject() {
    // Given
    api.post("/shop/carts/7/items", "{\"sku\":\"book\"}");

    // When
    api.post("/shop/carts/7/items", "{\"sku\":\"pen\"}");

    // Then
    JsonNode cart = contexts().get(0);
    assertThat(cart.get("name").asText()).isEqualTo("cart-7");
    assertThat(cart.at("/properties/status").asText()).isEqualTo("open");
    assertThat(cart.get("list").findValuesAsText("sku")).containsExactly("book", "pen");
    assertThat(cart.get("updateCount").asInt()).isGreaterThan(0);
    assertThat(api.get("/shop/carts/7").body()).isEqualTo("open 2");
    assertThat(api.get(API + "/projects/" + projectId + "/data").json().get("resources")).isEmpty();
  }

  @Test
  void editingAndDeletingStateChangesWhatStubsAnswer() {
    // Given
    api.post("/shop/carts/7/items", "{\"sku\":\"book\"}");

    // When
    ApiClient.Result edited = api.put(API + "/projects/" + projectId + "/state/context?name=cart-7",
        "{\"properties\":{\"status\":\"paid\"},\"list\":[{\"sku\":\"book\"},{\"sku\":\"lamp\"},{\"sku\":\"pen\"}]}");

    // Then
    assertThat(edited.status()).isEqualTo(200);
    assertThat(api.get("/shop/carts/7").body()).isEqualTo("paid 3");
    assertThat(api.delete(API + "/projects/" + projectId + "/state/context?name=cart-7").status()).isEqualTo(204);
    assertThat(api.get("/shop/carts/7").body()).isEqualTo("none 0");
    assertThat(api.delete(API + "/projects/" + projectId + "/state/context?name=cart-7").status()).isEqualTo(404);
    assertThat(api.put(API + "/projects/" + projectId + "/state/context?name=x", "{\"list\":5}").status()).isEqualTo(400);
  }

  @Test
  void stateSurvivesARestartAndTravelsInBackups(@TempDir Path other) {
    // Given
    api.post("/shop/carts/9/items", "{\"sku\":\"book\"}");
    server.close();
    server = MockHubServer.start(data);
    api = server.api();

    // When
    String afterRestart = api.get("/shop/carts/9").body();
    String backup = api.get(API + "/backup").body();

    // Then
    assertThat(afterRestart).isEqualTo("open 1");
    try (MockHubServer fresh = MockHubServer.start(other)) {
      fresh.api().post(API + "/restore", backup);
      assertThat(fresh.api().get("/shop/carts/9").body()).isEqualTo("open 1");
    }
  }

  @Test
  void clearingStateOrDeletingTheProjectRemovesIt() {
    // Given
    api.post("/shop/carts/1/items", "{\"sku\":\"book\"}");
    api.post("/shop/carts/2/items", "{\"sku\":\"pen\"}");

    // When
    api.delete(API + "/projects/" + projectId + "/state");

    // Then
    assertThat(contexts()).isEmpty();
    assertThat(api.get("/shop/carts/1").body()).isEqualTo("none 0");
    api.post("/shop/carts/3/items", "{\"sku\":\"lamp\"}");
    api.delete(API + "/projects/" + projectId);
    assertThat(data.resolve("mockhub/data/" + projectId + ".json")).doesNotExist();
  }
}
