package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatasetIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient api;
  private String datasets;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data);
    api = server.api();
    String orgId = api.post(API + "/orgs", "{\"name\":\"Retail\"}").json().get("id").asText();
    datasets = API + "/orgs/" + orgId + "/datasets";
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private void customers() {
    assertThat(api.post(datasets, "{\"key\":\"customers\",\"name\":\"Customers\",\"identity\":[\"id\"],\"indexes\":[\"city\"]}").status()).isEqualTo(201);
    ApiClient.Result uploaded = api.post(datasets + "/customers/upload?format=csv", "id,name,city,vip\n1,Ada,London,true\n2,Alan,Wilmslow,false\n3,Grace,London,false\n");
    assertThat(uploaded.status()).as(uploaded.body()).isEqualTo(200);
    assertThat(uploaded.json().get("added").asInt()).isEqualTo(3);
  }

  @Test
  void uploadsACsvAndFindsRecordsByField() {
    // Given
    customers();

    // When
    JsonNode london = api.get(datasets + "/customers/records?city=London&limit=1").json();

    // Then
    assertThat(london.get("total").asInt()).isEqualTo(2);
    assertThat(london.get("records").findValuesAsText("name")).containsExactly("Ada");
    JsonNode list = api.get(datasets).json();
    assertThat(list.at("/datasets/0/recordCount").asInt()).isEqualTo(3);
    assertThat(list.at("/datasets/0/columns").findValuesAsText("type")).containsExactly("number", "string", "string", "boolean");
    assertThat(list.at("/limits/maxRecords").asInt()).isEqualTo(100_000);
    assertThat(api.get(datasets + "/customers/records?q=wilm").json().get("total").asInt()).isEqualTo(1);
  }

  @Test
  void updatesAndAddsByIdentityFromNdjson() {
    // Given
    customers();

    // When
    JsonNode result = api.post(datasets + "/customers/upload?format=ndjson&mode=upsert",
        "{\"id\":2,\"name\":\"Alan Turing\",\"city\":\"Wilmslow\"}\n{\"id\":4,\"name\":\"Edsger\",\"city\":\"Austin\"}\n").json();

    // Then
    assertThat(result.get("added").asInt()).isEqualTo(1);
    assertThat(result.get("updated").asInt()).isEqualTo(1);
    assertThat(api.get(datasets + "/customers/records?id=2").json().at("/records/0/name").asText()).isEqualTo("Alan Turing");
    assertThat(result.at("/dataset/recordCount").asInt()).isEqualTo(4);
  }

  @Test
  void explainsBadUploadsAndUnknownDatasets() {
    // Given
    customers();

    // Then
    ApiClient.Result badCsv = api.post(datasets + "/customers/upload?format=csv", "id,name\n5,Barbara,extra\n");
    assertThat(badCsv.status()).isEqualTo(400);
    assertThat(badCsv.body()).contains("Line 2 has 3 values");
    assertThat(api.post(datasets + "/customers/upload?format=xml", "<a/>").status()).isEqualTo(400);
    assertThat(api.post(datasets + "/customers/upload?format=csv&mode=add", "id,name\n1,Again\n").json().get("skipped").asInt()).isEqualTo(1);
    assertThat(api.get(datasets + "/missing/records").status()).isEqualTo(404);
    assertThat(api.post(datasets, "{\"key\":\"customers\"}").status()).isEqualTo(409);
    assertThat(api.get(datasets + "/customers/records?a=1&b=2&c=3&d=4").status()).isEqualTo(400);
    assertThat(api.get(datasets + "/customers/records?limit=9999").status()).isEqualTo(400);
  }

  @Test
  void datasetsSurviveARestartAndGoWithTheirOrganisation() {
    // Given
    customers();
    String orgUrl = datasets.substring(0, datasets.length() - "/datasets".length());
    server.close();
    server = MockHubServer.start(data);
    api = server.api();

    // When
    JsonNode afterRestart = api.get(datasets + "/customers/records").json();
    ApiClient.Result deleted = api.delete(orgUrl);

    // Then
    assertThat(afterRestart.get("total").asInt()).isEqualTo(3);
    assertThat(deleted.status()).as(deleted.body()).isIn(200, 204);
    assertThat(data.resolve("mockhub/datasets").toFile().list()).isEmpty();
  }
}
