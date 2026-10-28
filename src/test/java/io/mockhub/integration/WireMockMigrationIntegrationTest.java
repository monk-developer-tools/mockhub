package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.support.Zips;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WireMockMigrationIntegrationTest {

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

  static byte[] wireMockFolder() {
    Map<String, String> entries = new LinkedHashMap<>();
    entries.put("wiremock/mappings/orders.json",
        "{\"mappings\":[{\"request\":{\"method\":\"GET\",\"urlPathPattern\":\"/shop/v1/orders/[a-z0-9]+\"},"
            + "\"response\":{\"status\":200,\"bodyFileName\":\"orders/order.json\",\"transformers\":[\"response-template\"],"
            + "\"headers\":{\"Content-Type\":\"application/json\"}}}]}");
    entries.put("wiremock/__files/orders/order.json", "{\"id\":\"{{request.pathSegments.[3]}}\",\"state\":\"PACKED\"}");
    return Zips.of(entries);
  }

  @Test
  void anExistingWireMockFolderKeepsItsUrlsAndBodyFilesAtTheRoot() {
    // Given
    String id = api.post(API + "/projects", "{\"name\":\"Shop\",\"slug\":\"shop\",\"root\":true}").json().get("id").asText();

    // When
    JsonNode imported = api.postBytes(API + "/projects/" + id + "/import/wiremock-zip", wireMockFolder(), "application/zip").json();

    // Then
    assertThat(imported.get("imported").asInt()).isEqualTo(1);
    assertThat(imported.get("files").asInt()).isEqualTo(1);
    assertThat(imported.get("missingFiles")).isEmpty();
    ApiClient.Result order = api.get("/shop/v1/orders/ab12");
    assertThat(order.status()).isEqualTo(200);
    assertThat(order.json().get("id").asText()).isEqualTo("ab12");
    assertThat(api.get(API + "/projects/" + id + "/files").json()).extracting(f -> f.get("name").asText()).containsExactly("orders/order.json");
  }

  @Test
  void otherProjectsKeepTheirOwnPathsAndRequestLogs() {
    // Given
    String root = api.post(API + "/projects", "{\"name\":\"Shop\",\"slug\":\"shop\",\"root\":true}").json().get("id").asText();
    api.postBytes(API + "/projects/" + root + "/import/wiremock-zip", wireMockFolder(), "application/zip");
    String billing = api.post(API + "/projects", "{\"name\":\"Billing\",\"slug\":\"billing\"}").json().get("id").asText();
    api.post(API + "/projects/" + billing + "/stubs", "{\"request\":{\"method\":\"GET\",\"url\":\"/invoices\"},\"response\":{\"status\":200,\"body\":\"[]\"}}");

    // When
    ApiClient.Result invoices = api.get("/billing/invoices");
    api.get("/shop/v1/orders/x1");
    api.delete(API + "/projects/" + root + "/requests");

    // Then
    assertThat(invoices.body()).isEqualTo("[]");
    assertThat(api.get(API + "/projects/" + billing + "/requests").json()).hasSize(1);
    assertThat(api.get(API + "/projects/" + root + "/requests").json()).isEmpty();
  }

  @Test
  void aSecondRootProjectIsRefused() {
    // Given
    api.post(API + "/projects", "{\"name\":\"Shop\",\"root\":true}");

    // When
    ApiClient.Result second = api.post(API + "/projects", "{\"name\":\"Other\",\"root\":true}");

    // Then
    assertThat(second.status()).isEqualTo(409);
  }
}
