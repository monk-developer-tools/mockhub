package io.mockhub.importer;

import static io.mockhub.support.Fixtures.project;
import static io.mockhub.support.Fixtures.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PostmanImporterTest {

  private final PostmanImporter importer = new PostmanImporter();

  private static ObjectNode named(List<ObjectNode> stubs, String name) {
    return stubs.stream().filter(s -> s.path("name").asText().equals(name)).findFirst().orElseThrow();
  }

  @Test
  void turnsEverySavedExampleIntoAStubWithItsFolderPathAndAnswer() {
    // Given
    String collection = resource("shop.postman_collection.json");

    // When
    StubImporter.Result result = importer.parse(new StubImporter.Request(project("shop"), collection, Map.of()));

    // Then
    List<ObjectNode> stubs = result.stubs();
    assertThat(stubs).extracting(s -> s.path("name").asText())
        .containsExactly("Get order · Found", "Get order · Missing", "Search orders", "Create order · Created", "Health");
    ObjectNode found = named(stubs, "Get order · Found");
    assertThat(found.at("/request/urlPathTemplate").asText()).isEqualTo("/api/orders/{id}");
    assertThat(found.at("/response/status").asInt()).isEqualTo(200);
    assertThat(found.at("/response/body").asText()).isEqualTo("{\"id\": 7, \"status\": \"PAID\"}");
    assertThat(found.at("/response/headers").has("Date")).isFalse();
    assertThat(found.at("/response/headers/Content-Type").asText()).isEqualTo("application/json");
    assertThat(found.at("/metadata/mockhub/folder").asText()).isEqualTo("Orders");
    assertThat(found.at("/metadata/mockhub/description").asText()).isEqualTo("One order by id");
    assertThat(named(stubs, "Get order · Missing").at("/request/urlPath").asText()).isEqualTo("/api/orders/999");
    assertThat(named(stubs, "Get order · Missing").at("/response/status").asInt()).isEqualTo(404);
    ObjectNode search = named(stubs, "Search orders");
    assertThat(search.at("/request/queryParameters/status/equalTo").asText()).isEqualTo("PAID");
    assertThat(search.at("/request/queryParameters").has("page")).isFalse();
    assertThat(search.at("/priority").asInt()).isEqualTo(5);
    ObjectNode create = named(stubs, "Create order · Created");
    assertThat(create.at("/request/urlPath").asText()).isEqualTo("/api/orders");
    assertThat(create.at("/request/bodyPatterns/0/equalToJson").asText()).isEqualTo("{\"sku\":\"A1\"}");
    assertThat(create.at("/response/headers/Location").asText()).isEqualTo("/api/orders/8");
    assertThat(named(stubs, "Health").at("/response/status").asInt()).isEqualTo(200);
    assertThat(result.details().get("collection").asText()).isEqualTo("Shop API");
    assertThat(result.details().get("requests").asInt()).isEqualTo(4);
    assertThat(result.details().get("examples").asInt()).isEqualTo(5);
    assertThat(result.details().get("skipped").toString())
        .contains("Create order · Again: answers the same request as an earlier example")
        .contains("Health: no saved example response");
  }

  @Test
  void stripsAPrefixAndLeavesOutRequestsOutsideIt() {
    // Given
    String collection = "{\"info\":{\"name\":\"c\"},\"item\":[{\"name\":\"In\",\"request\":{\"method\":\"GET\",\"url\":\"{{host}}/api/v1/users/{{userId}}\"}},"
        + "{\"name\":\"Out\",\"request\":{\"method\":\"GET\",\"url\":\"{{host}}/admin/ping\"}}]}";

    // When
    StubImporter.Result result = importer.parse(new StubImporter.Request(project("c"), collection, Map.of("stripPrefix", "api/v1/")));

    // Then
    assertThat(result.stubs()).singleElement().satisfies(s -> assertThat(s.at("/request/urlPathTemplate").asText()).isEqualTo("/users/{userId}"));
    assertThat(result.details().get("skipped").toString()).contains("Out: its URL is outside the stripped prefix /api/v1");
  }

  @Test
  void refusesFilesThatAreNotAVersionTwoCollection() {
    // Given
    // When / Then
    assertThatThrownBy(() -> importer.parse(new StubImporter.Request(project("c"), "not json", Map.of()))).hasMessageContaining("isn't valid JSON");
    assertThatThrownBy(() -> importer.parse(new StubImporter.Request(project("c"), "{\"requests\":[]}", Map.of()))).hasMessageContaining("v1 collection");
    assertThatThrownBy(() -> importer.parse(new StubImporter.Request(project("c"), "{\"openapi\":\"3.0.0\"}", Map.of()))).hasMessageContaining("no item list");
    assertThatThrownBy(() -> importer.parse(new StubImporter.Request(project("c"), "{\"item\":[]}", Map.of()))).hasMessageContaining("no requests");
  }
}
