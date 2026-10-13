package io.mockhub.export;

import static io.mockhub.support.Fixtures.project;
import static io.mockhub.support.Fixtures.stub;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostmanExporterTest {

  private final PostmanExporter exporter = new PostmanExporter();

  @Test
  void producesAV21CollectionGroupedIntoFoldersWithStatusTests() throws Exception {
    // Given
    var stubs = List.of(
        stub("{'name':'List users','request':{'method':'GET','urlPath':'/pay/users','queryParameters':{'page':{'equalTo':'1'}}},'response':{'status':200}}"),
        stub("{'name':'Create order','request':{'method':'POST','urlPath':'/pay/orders','bodyPatterns':[{'equalToJson':'{\\\"a\\\":1}'}]},'response':{'status':201}}"));

    // When
    JsonNode collection = Jsons.MAPPER.readTree(exporter.export(project("pay"), stubs, "https://mocks.acme.com/pay"));

    // Then
    assertThat(collection.at("/info/schema").asText()).contains("v2.1.0");
    assertThat(collection.at("/variable/0/key").asText()).isEqualTo("baseUrl");
    assertThat(collection.at("/variable/0/value").asText()).isEqualTo("https://mocks.acme.com/pay");
    assertThat(collection.get("item")).extracting(f -> f.get("name").asText()).containsExactly("users", "orders");

    JsonNode listUsers = collection.at("/item/0/item/0");
    assertThat(listUsers.at("/request/url/raw").asText()).isEqualTo("{{baseUrl}}/users?page=1");
    assertThat(listUsers.at("/request/url/query/0/key").asText()).isEqualTo("page");
    assertThat(listUsers.at("/event/0/script/exec/1").asText()).contains("have.status(200)");

    JsonNode createOrder = collection.at("/item/1/item/0");
    assertThat(createOrder.at("/request/body/mode").asText()).isEqualTo("raw");
    assertThat(createOrder.at("/request/body/options/raw/language").asText()).isEqualTo("json");
  }

  @Test
  void describesItsFile() {
    assertThat(exporter.format()).isEqualTo("postman");
    assertThat(exporter.fileName(project("pay"))).isEqualTo("pay.postman_collection.json");
  }
}
