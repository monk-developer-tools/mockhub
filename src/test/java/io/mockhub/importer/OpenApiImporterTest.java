package io.mockhub.importer;

import static io.mockhub.support.Fixtures.project;
import static io.mockhub.support.Fixtures.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenApiImporterTest {

  private final OpenApiImporter importer = new OpenApiImporter();

  @Test
  void createsAStubPerOperationWithExamplesOrSchemaSamples() {
    // When
    StubImporter.Result result = importer.parse(new StubImporter.Request(project("pay"), resource("petstore.yaml"), Map.of()));

    // Then
    assertThat(result.details().get("title").asText()).isEqualTo("Petstore");
    assertThat(result.stubs()).hasSize(3);

    ObjectNode list = result.stubs().get(0);
    assertThat(list.get("name").asText()).isEqualTo("List pets");
    assertThat(list.at("/request/urlPath").asText()).isEqualTo("/pets");
    assertThat(list.at("/response/jsonBody/0/name").asText()).isEqualTo("Fido");
    assertThat(list.at("/response/jsonBody/0/status").asText()).isEqualTo("available");
    assertThat(list.at("/response/jsonBody/0/born").asText()).isEqualTo("2024-01-01");
    assertThat(list.at("/metadata/mockhub/folder").asText()).isEqualTo("pets");

    ObjectNode create = result.stubs().get(1);
    assertThat(create.get("name").asText()).isEqualTo("createPet");
    assertThat(create.at("/response/status").asInt()).isEqualTo(201);
    assertThat(create.at("/response/jsonBody/name").asText()).isEqualTo("Rex");

    ObjectNode get = result.stubs().get(2);
    assertThat(get.at("/request/urlPathTemplate").asText()).isEqualTo("/pets/{petId}");
    assertThat(get.at("/response/jsonBody/id").asInt()).isEqualTo(1);
  }

  @Test
  void acceptsJsonSwagger2() {
    // Given
    String swagger = "{\"swagger\":\"2.0\",\"info\":{\"title\":\"t\"},\"paths\":{\"/a\":{\"get\":{\"responses\":{\"200\":{\"schema\":{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"boolean\"}}}}}}}}}";

    // When
    StubImporter.Result result = importer.parse(new StubImporter.Request(project("pay"), swagger, Map.of()));

    // Then
    assertThat(result.stubs()).singleElement().satisfies(s -> assertThat(s.at("/response/jsonBody/ok").asBoolean()).isTrue());
  }

  @Test
  void stripsAServerPrefixWhenAsked() {
    // Given
    String spec = "{\"openapi\":\"3.0.0\",\"paths\":{\"/api/v1/a\":{\"get\":{\"responses\":{\"204\":{\"description\":\"x\"}}}}}}";

    // When
    StubImporter.Result result = importer.parse(new StubImporter.Request(project("pay"), spec, Map.of("stripPrefix", "/api/v1")));

    // Then
    assertThat(result.stubs().get(0).at("/request/urlPath").asText()).isEqualTo("/a");
    assertThat(result.stubs().get(0).at("/response/status").asInt()).isEqualTo(204);
  }

  @Test
  void rejectsDocumentsThatAreNotOpenApi() {
    assertThatThrownBy(() -> importer.parse(new StubImporter.Request(project("pay"), "{\"hello\":1}", Map.of())))
        .hasMessageContaining("Not an OpenAPI");
  }
}
