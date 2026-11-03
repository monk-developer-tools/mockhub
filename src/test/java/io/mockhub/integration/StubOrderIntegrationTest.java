package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StubOrderIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient api;
  private String projectId;
  private String specific;
  private String catchAll;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data);
    api = server.api();
    projectId = api.post(API + "/projects", "{\"name\":\"Shop\",\"slug\":\"shop\"}").json().get("id").asText();
    specific = stub("{\"name\":\"Special item\",\"request\":{\"method\":\"GET\",\"urlPath\":\"/items/special\"},\"response\":{\"status\":200,\"body\":\"special\"}}");
    catchAll = stub("{\"name\":\"Any item\",\"request\":{\"method\":\"GET\",\"urlPathPattern\":\"/items/.*\"},\"response\":{\"status\":200,\"body\":\"any\"}}");
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private String stub(String json) {
    return api.post(API + "/projects/" + projectId + "/stubs", json).json().get("id").asText();
  }

  private List<String> order() {
    List<String> ids = new ArrayList<>();
    api.get(API + "/projects/" + projectId + "/matching-order").json().forEach(n -> ids.add(n.asText()));
    return ids;
  }

  @Test
  void showsTheOrderWireMockTriesStubsIn() {
    // When
    List<String> ids = order();
    String answer = api.get("/shop/items/special").body();

    // Then
    assertThat(ids).containsExactly(catchAll, specific);
    assertThat(answer).isEqualTo("any");
  }

  @Test
  void reorderingRewritesPrioritiesSoTheFirstStubWins() {
    // When
    ApiClient.Result result = api.put(API + "/projects/" + projectId + "/matching-order", "{\"ids\":[\"" + specific + "\",\"" + catchAll + "\"]}");

    // Then
    assertThat(result.status()).isEqualTo(200);
    assertThat(order()).containsExactly(specific, catchAll);
    assertThat(api.get("/shop/items/special").body()).isEqualTo("special");
    assertThat(api.get("/shop/items/other").body()).isEqualTo("any");
    JsonNode stubs = api.get(API + "/projects/" + projectId + "/stubs").json();
    assertThat(stubs.get(0).get("priority").asInt()).isEqualTo(10);
    assertThat(stubs.get(1).get("priority").asInt()).isEqualTo(20);
  }

  @Test
  void refusesAnOrderThatLeavesStubsOut() {
    // When
    ApiClient.Result result = api.put(API + "/projects/" + projectId + "/matching-order", "{\"ids\":[\"" + specific + "\"]}");

    // Then
    assertThat(result.status()).isEqualTo(400);
    assertThat(result.body()).contains("every stub");
  }

  @Test
  void theDefaultResponseStaysLastAndOutOfTheOrder() {
    // Given
    api.put(API + "/projects/" + projectId + "/runtime", "{\"defaultResponse\":{\"status\":404,\"body\":\"none\"}}");

    // When
    api.put(API + "/projects/" + projectId + "/matching-order", "{\"ids\":[\"" + specific + "\",\"" + catchAll + "\"]}");

    // Then
    assertThat(order()).containsExactly(specific, catchAll);
    assertThat(api.get("/shop/other").body()).isEqualTo("none");
  }
}
