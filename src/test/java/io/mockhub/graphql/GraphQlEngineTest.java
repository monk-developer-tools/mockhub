package io.mockhub.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.Jsons;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphQlEngineTest {

  static final String SHOP = """
      scalar DateTime
      directive @auth(role: String) on FIELD_DEFINITION
      enum Status { ACTIVE BLOCKED }
      interface Node { id: ID! }
      type User implements Node { id: ID! name: String! email: String status: Status createdAt: DateTime orders(first: Int): [Order!]! }
      type Order implements Node { id: ID! total: Float! items: [String!]! }
      union SearchResult = User | Order
      input NewUser { name: String! email: String }
      type Query {
        user(id: ID!): User
        users(first: Int): [User!]!
        node(id: ID!): Node
        search(text: String!): [SearchResult!]!
        secret: String @auth(role: "admin") @key(fields: "id")
      }
      type Mutation { createUser(input: NewUser!): User! }
      """;

  private final GraphQlEngine engine = new GraphQlEngine(new MockSchemas());

  @SuppressWarnings("unchecked")
  private static Map<String, Object> data(Map<String, Object> result) {
    assertThat(result).as(String.valueOf(result.get("errors"))).doesNotContainKey("errors");
    return (Map<String, Object>) result.get("data");
  }

  @Test
  void answersExactlyTheFieldsAskedForWithRealisticValues() {
    // Given
    String query = "query GetUser($id: ID!) { user(id: $id) { id name email status createdAt orders(first: 2) { total } } }";

    // When
    Map<String, Object> result = engine.execute(SHOP, query, "GetUser", Map.of("id", "42"));

    // Then
    Map<String, Object> user = (Map<String, Object>) data(result).get("user");
    assertThat(user).containsOnlyKeys("id", "name", "email", "status", "createdAt", "orders");
    assertThat(user.get("id")).isEqualTo("42");
    assertThat((String) user.get("email")).contains("@");
    assertThat(user.get("status")).isIn("ACTIVE", "BLOCKED");
    assertThat((String) user.get("createdAt")).matches("\\d{4}-\\d{2}-\\d{2}T.*");
    assertThat((List<?>) user.get("orders")).hasSize(2);
  }

  @Test
  void givesTheSameAnswerForTheSameQueryAndADifferentOneForAnotherId() {
    // Given
    String query = "{ user(id: \"1\") { name } other: user(id: \"2\") { name } }";

    // When
    Map<String, Object> first = engine.execute(SHOP, query, null, null);
    Map<String, Object> second = engine.execute(SHOP, query, null, null);

    // Then
    assertThat(first).isEqualTo(second);
    Map<String, Object> data = data(first);
    assertThat(((Map<?, ?>) data.get("user")).get("name")).isNotEqualTo(((Map<?, ?>) data.get("other")).get("name"));
  }

  @Test
  void echoesMutationInputAndResolvesInterfacesAndUnions() {
    // Given
    String query = """
        mutation { createUser(input: {name: "Ada", email: "ada@example.com"}) { name email } }""";
    String search = "{ search(text: \"x\") { __typename ... on User { name } ... on Order { total } } node(id: \"7\") { id __typename } }";

    // When
    Map<String, Object> created = data(engine.execute(SHOP, query, null, null));
    Map<String, Object> found = data(engine.execute(SHOP, search, null, null));

    // Then
    assertThat((Map<String, Object>) created.get("createUser")).containsEntry("name", "Ada").containsEntry("email", "ada@example.com");
    assertThat((List<Map<String, Object>>) found.get("search")).allSatisfy(r -> assertThat(r.get("__typename")).isIn("User", "Order"));
    assertThat((Map<String, Object>) found.get("node")).containsEntry("id", "7");
  }

  @Test
  void reportsInvalidQueriesAsGraphQlErrors() {
    // When
    Map<String, Object> result = engine.execute(SHOP, "{ user(id: 1) { nope } }", null, null);

    // Then
    assertThat(Jsons.MAPPER.valueToTree(result).at("/errors/0/message").asText()).contains("nope");
  }

  @Test
  void answersIntrospectionSoGraphQlToolsWork() {
    // When
    Map<String, Object> data = data(engine.execute(SHOP, "{ __schema { queryType { name } } }", null, null));

    // Then
    assertThat(Jsons.MAPPER.valueToTree(data).at("/__schema/queryType/name").asText()).isEqualTo("Query");
  }

  @Test
  void explainsABrokenSchema() {
    // Then
    assertThatThrownBy(() -> engine.execute("type Query { a: Missing }", "{ a }", null, null))
        .isInstanceOf(GraphQlException.class).hasMessageContaining("Missing");
    assertThatThrownBy(() -> engine.execute("type Query {", "{ a }", null, null))
        .isInstanceOf(GraphQlException.class).hasMessageStartingWith("The schema isn't valid GraphQL");
  }

  @Test
  void answersSubscriptionsWithAClearErrorInsteadOfFailing() {
    // Given
    String sdl = "type Query { hello: String } type Subscription { ticks: Int }";
    String both = "query Hi { hello } subscription Ticks { ticks }";

    // When
    Map<String, Object> subscription = engine.execute(sdl, "subscription { ticks }", null, Map.of());
    Map<String, Object> named = engine.execute(sdl, both, "Ticks", Map.of());
    Map<String, Object> query = engine.execute(sdl, both, "Hi", Map.of());

    // Then
    assertThat(subscription.toString()).contains("Subscriptions need a WebSocket or SSE connection");
    assertThat(subscription).doesNotContainKey("data");
    assertThat(named.toString()).contains("Subscriptions need a WebSocket");
    assertThat(query).containsKey("data");
    assertThat(GraphQlEngine.subscription("{ broken", null)).isFalse();
  }
}
