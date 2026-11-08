package io.mockhub.importer;

import static io.mockhub.support.Fixtures.project;
import static io.mockhub.support.Fixtures.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
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
    assertThat(list.at("/response/jsonBody/0/born").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
    assertThat(list.at("/metadata/mockhub/folder").asText()).isEqualTo("pets");

    ObjectNode create = result.stubs().get(1);
    assertThat(create.get("name").asText()).isEqualTo("createPet");
    assertThat(create.at("/response/status").asInt()).isEqualTo(201);
    assertThat(create.at("/response/jsonBody/name").asText()).isEqualTo("Rex");

    ObjectNode get = result.stubs().get(2);
    assertThat(get.at("/request/urlPathTemplate").asText()).isEqualTo("/pets/{petId}");
    assertThat(get.at("/response/jsonBody/id").asInt()).isPositive();
  }

  @Test
  void fillsSchemasWithRealisticRepeatableData() {
    // Given
    String spec = """
        {"openapi":"3.0.0","paths":{"/customers":{"get":{"responses":{"200":{"description":"ok","content":{"application/json":{
          "schema":{"type":"array","items":{"type":"object","properties":{
            "id":{"type":"string","format":"uuid"},"name":{"type":"string"},"email":{"type":"string"},
            "city":{"type":"string"},"phone":{"type":"string"},"createdAt":{"type":"string","format":"date-time"},
            "price":{"type":"number"},"quantity":{"type":"integer"},"format":{"type":"string"}}}}}}}}}}}}""";

    // When
    ObjectNode first = importer.parse(new StubImporter.Request(project("pay"), spec, Map.of())).stubs().get(0);
    ObjectNode again = importer.parse(new StubImporter.Request(project("pay"), spec, Map.of())).stubs().get(0);

    // Then
    var items = first.at("/response/jsonBody");
    assertThat(items).hasSize(3);
    var customer = items.get(0);
    assertThat(customer.get("id").asText()).hasSize(36);
    assertThat(customer.get("name").asText()).contains(" ");
    assertThat(customer.get("email").asText()).contains("@");
    assertThat(customer.get("phone").asText()).containsPattern("\\d");
    assertThat(customer.get("createdAt").asText()).contains("T");
    assertThat(customer.get("price").isNumber()).isTrue();
    assertThat(customer.get("quantity").asInt()).isBetween(1, 9);
    assertThat(customer.get("format").asText()).doesNotContainPattern("^\\d{4}-");
    assertThat(items.get(1).get("name").asText()).isNotEqualTo(customer.get("name").asText());
    assertThat(again.at("/response/jsonBody")).isEqualTo(items);
  }

  @Test
  void acceptsJsonSwagger2() {
    // Given
    String swagger = "{\"swagger\":\"2.0\",\"info\":{\"title\":\"t\"},\"paths\":{\"/a\":{\"get\":{\"responses\":{\"200\":{\"schema\":{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"boolean\"}}}}}}}}}";

    // When
    StubImporter.Result result = importer.parse(new StubImporter.Request(project("pay"), swagger, Map.of()));

    // Then
    assertThat(result.stubs()).singleElement().satisfies(s -> assertThat(s.at("/response/jsonBody/ok").isBoolean()).isTrue());
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

  @Test
  void buildsOnlyAsManyStubsAsTheLimitButListsEveryOperation() {
    // When
    StubImporter.Result result = importer.parse(new StubImporter.Request(project("pay"), resource("petstore.yaml"), Map.of("limit", "2")));

    // Then
    assertThat(result.stubs()).hasSize(2);
    assertThat(result.details().get("operations")).hasSize(3);
  }

  @Test
  void keepsSampleBodiesSmallWhenSchemasFanOut() {
    // Given
    StringBuilder spec = new StringBuilder("""
        {"openapi": "3.0.0", "info": {"title": "Wide", "version": "1"},
         "paths": {"/a": {"get": {"responses": {"200": {"content": {"application/json":
           {"schema": {"$ref": "#/components/schemas/S0"}}}}}}}},
         "components": {"schemas": {""");
    for (int level = 0; level < 8; level++) {
      if (level > 0) spec.append(',');
      spec.append("\"S").append(level).append("\": {\"type\": \"object\", \"properties\": {");
      for (int p = 0; p < 30; p++) {
        if (p > 0) spec.append(',');
        spec.append("\"p").append(p).append("\": {\"$ref\": \"#/components/schemas/S").append(level + 1).append("\"}");
      }
      spec.append("}}");
    }
    spec.append(",\"S8\": {\"type\": \"string\"}}}}");

    // When
    long started = System.nanoTime();
    StubImporter.Result result = importer.parse(new StubImporter.Request(project("pay"), spec.toString(), Map.of()));

    // Then
    assertThat(nodes(result.stubs().get(0).at("/response/jsonBody"))).isLessThanOrEqualTo(OpenApiImporter.MAX_NODES);
    assertThat(System.nanoTime() - started).isLessThan(5_000_000_000L);
  }

  @Test
  void fillsDeeplyReferencedSchemasWithoutNullsAndLeavesNoNullArrayItems() {
    // Given
    String spec = """
        {"openapi": "3.0.0", "info": {"title": "Deep", "version": "1"},
         "paths": {"/track": {"get": {"responses": {"200": {"content": {"application/json":
           {"schema": {"$ref": "#/components/schemas/Api"}}}}}}}},
         "components": {"schemas": {
           "Api": {"type": "object", "properties": {"response": {"$ref": "#/components/schemas/Response"}}},
           "Response": {"type": "object", "properties": {"shipment": {"type": "array", "items": {"$ref": "#/components/schemas/Shipment"}}}},
           "Shipment": {"type": "object", "properties": {"package": {"type": "array", "items": {"$ref": "#/components/schemas/Package"}}}},
           "Package": {"type": "object", "properties": {"alert": {"type": "array", "items": {"$ref": "#/components/schemas/Alert"}}}},
           "Alert": {"type": "object", "properties": {"code": {"type": "string", "maximum": 1, "maxLength": 10}}}}}}
        """;

    // When
    JsonNode body = importer.parse(new StubImporter.Request(project("pay"), spec, Map.of())).stubs().get(0).at("/response/jsonBody");

    // Then
    assertThat(body.at("/response/shipment/0/package/0/alert/0/code").isTextual()).as(body.toString()).isTrue();
    assertThat(body.toString()).doesNotContain("null");
  }

  private static int nodes(JsonNode node) {
    int count = 1;
    for (JsonNode child : node) count += nodes(child);
    return count;
  }

  @Test
  void resolvesNamedExamplesThatPointAtComponents() {
    // Given
    String spec = """
        {
         "openapi": "3.0.3",
         "info": {
          "title": "t",
          "version": "1"
         },
         "paths": {
          "/user": {
           "get": {
            "responses": {
             "200": {
              "description": "ok",
              "content": {
               "application/json": {
                "schema": {
                 "type": "object"
                },
                "examples": {
                 "default": {
                  "$ref": "#/components/examples/user"
                 }
                }
               }
              }
             }
            }
           }
          },
          "/issue": {
           "get": {
            "responses": {
             "200": {
              "description": "ok",
              "content": {
               "application/json": {
                "examples": {
                 "default": {
                  "summary": "Issue",
                  "value": {
                   "$ref": "#/components/examples/issue"
                  }
                 }
                }
               }
              }
             }
            }
           }
          }
         },
         "components": {
          "examples": {
           "user": {
            "value": {
             "login": "octocat",
             "id": 1
            }
           },
           "issue": {
            "value": {
             "number": 1347
            }
           }
          }
         }
        }
        """;

    // When
    StubImporter.Result result = importer.parse(new StubImporter.Request(project("gh"), spec, Map.of()));

    // Then
    assertThat(result.stubs()).hasSize(2);
    assertThat(result.stubs()).anySatisfy(s -> assertThat(s.at("/response/jsonBody/login").asText()).isEqualTo("octocat"));
    assertThat(result.stubs()).anySatisfy(s -> assertThat(s.at("/response/jsonBody/number").asInt()).isEqualTo(1347));
    assertThat(result.stubs()).noneSatisfy(s -> assertThat(s.at("/response/jsonBody").has("$ref")).isTrue());
  }

  @Test
  void importsOpenApi32QueryOperationsAsQueryStubs() {
    // Given
    String spec = "{\"openapi\":\"3.2.0\",\"info\":{\"title\":\"t\",\"version\":\"1\"},\"paths\":{\"/search\":{\"query\":{\"responses\":"
        + "{\"200\":{\"description\":\"ok\",\"content\":{\"application/json\":{\"example\":{\"hits\":2}}}}}}}}}";

    // When
    StubImporter.Result result = importer.parse(new StubImporter.Request(project("s"), spec, Map.of()));

    // Then
    assertThat(result.stubs()).singleElement().satisfies(s -> {
      assertThat(s.at("/request/method").asText()).isEqualTo("QUERY");
      assertThat(s.at("/response/jsonBody/hits").asInt()).isEqualTo(2);
    });
  }
}
