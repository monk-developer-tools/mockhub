package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphQlIntegrationTest {

  private static final String API = "/__admin/mockhub/api";
  private static final String SCHEMA = """
      type Book { id: ID! title: String! author: Author! }
      type Author { id: ID! name: String! }
      type Query { book(id: ID!): Book books(first: Int): [Book!]! }
      type Mutation { addBook(title: String!): Book! }
      """;

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient api;
  private String projectId;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data);
    api = server.api();
    projectId = api.post(API + "/projects", "{\"name\":\"Library\",\"slug\":\"library\"}").json().get("id").asText();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private JsonNode post(String body) {
    ApiClient.Result r = api.post("/library/graphql", body);
    assertThat(r.status()).as(r.body()).isEqualTo(200);
    return r.json();
  }

  private static String body(String query, String operationName, String variables) {
    return Jsons.object().put("query", query).put("operationName", operationName).set("variables", read(variables)).toString();
  }

  private static JsonNode read(String json) {
    try {
      return Jsons.MAPPER.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void importsASchemaAndAnswersQueriesWithGeneratedData() {
    // Given
    ApiClient.Result imported = api.post(API + "/projects/" + projectId + "/import/graphql?name=Library", SCHEMA);

    // When
    JsonNode book = post(body("query GetBook($id: ID!) { book(id: $id) { id title author { name } } }", "GetBook", "{\"id\":\"b-1\"}"));
    JsonNode list = post(body("{ books(first: 4) { title } }", null, "{}"));
    ApiClient.Result get = api.get("/library/graphql?query=" + URLEncoder.encode("{ books { id } }", StandardCharsets.UTF_8));

    // Then
    assertThat(imported.status()).as(imported.body()).isEqualTo(200);
    assertThat(imported.json().get("imported").asInt()).isEqualTo(1);
    assertThat(imported.json().get("operations").findValuesAsText("operationName")).containsExactly("Book", "Books", "AddBook");
    assertThat(book.at("/data/book/id").asText()).isEqualTo("b-1");
    assertThat(book.at("/data/book/author/name").asText()).isNotBlank();
    assertThat(list.at("/data/books").size()).isEqualTo(4);
    assertThat(get.json().at("/data/books").size()).isEqualTo(3);
  }

  @Test
  void anOperationStubWinsForItsOperationNameAndVariables() {
    // Given
    api.post(API + "/projects/" + projectId + "/import/graphql", SCHEMA);
    api.post(API + "/projects/" + projectId + "/stubs", """
        {"name":"Missing book","request":{"method":"POST","urlPath":"/graphql","bodyPatterns":[
           {"matchesJsonPath":"$[?(@.operationName == 'GetBook')]"},
           {"matchesJsonPath":{"expression":"$.variables","equalToJson":"{\\"id\\":\\"404\\"}","ignoreExtraElements":true}}]},
         "response":{"status":200,"jsonBody":{"data":{"book":null}}},
         "metadata":{"mockhub":{"type":"graphql"}}}""");
    String query = "query GetBook($id: ID!) { book(id: $id) { title } }";

    // When
    JsonNode missing = post(body(query, "GetBook", "{\"id\":\"404\"}"));
    JsonNode found = post(body(query, "GetBook", "{\"id\":\"1\"}"));

    // Then
    assertThat(missing.at("/data/book").isNull()).isTrue();
    assertThat(found.at("/data/book/title").asText()).isNotBlank();
  }

  @Test
  void answersBatchesAndExplainsBadRequests() {
    // Given
    api.post(API + "/projects/" + projectId + "/import/graphql", SCHEMA);

    // When
    JsonNode batch = post("[" + body("{ books { id } }", null, "{}") + "," + body("mutation { addBook(title: \"Dune\") { title } }", null, "{}") + "]");
    ApiClient.Result notJson = api.post("/library/graphql", "{ books { id } ");
    JsonNode invalid = post(body("{ shelves { id } }", null, "{}"));

    // Then
    assertThat(batch.size()).isEqualTo(2);
    assertThat(batch.at("/1/data/addBook/title").asText()).isEqualTo("Dune");
    assertThat(notJson.status()).isEqualTo(400);
    assertThat(notJson.json().at("/errors/0/message").asText()).startsWith("The body isn't valid JSON");
    assertThat(invalid.at("/errors/0/message").asText()).contains("shelves");
  }

  @Test
  void importsAnIntrospectionResultAndListsExampleOperations() {
    // Given
    api.post(API + "/projects/" + projectId + "/import/graphql?name=Library", SCHEMA);
    String introspection = post(body("""
        query { __schema { queryType { name } mutationType { name } subscriptionType { name }
          types { kind name description fields(includeDeprecated: true) { name description args { name description type { ...T } defaultValue }
            type { ...T } isDeprecated deprecationReason } inputFields { name description type { ...T } defaultValue }
            interfaces { ...T } enumValues(includeDeprecated: true) { name description isDeprecated deprecationReason } possibleTypes { ...T } }
          directives { name description locations args { name description type { ...T } defaultValue } } } }
        fragment T on __Type { kind name ofType { kind name ofType { kind name ofType { kind name } } } }""", null, "{}")).toString();
    String other = api.post(API + "/projects", "{\"name\":\"Copy\",\"slug\":\"copy\"}").json().get("id").asText();

    // When
    ApiClient.Result imported = api.post(API + "/projects/" + other + "/import/graphql?path=/api", introspection);
    JsonNode operations = api.post(API + "/projects/" + other + "/graphql/operations", Jsons.object().put("schema", SCHEMA).toString()).json();
    ApiClient.Result broken = api.post(API + "/projects/" + other + "/graphql/operations", "{\"schema\":\"type Query {\"}");

    // Then
    assertThat(imported.status()).as(imported.body()).isEqualTo(200);
    assertThat(api.post("/copy/api", body("{ books { title } }", null, "{}")).json().at("/data/books").size()).isEqualTo(3);
    assertThat(operations.at("/operations/0/query").asText()).startsWith("query Book($id: ID!)");
    assertThat(broken.status()).isEqualTo(400);
  }
}
