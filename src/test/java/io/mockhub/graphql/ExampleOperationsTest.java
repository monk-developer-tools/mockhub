package io.mockhub.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExampleOperationsTest {

  private final MockSchemas schemas = new MockSchemas();
  private final ExampleOperations examples = new ExampleOperations(schemas);
  private final GraphQlEngine engine = new GraphQlEngine(schemas);

  @Test
  void writesOneRunnableOperationPerQueryAndMutationField() {
    // When
    List<ExampleOperations.Operation> operations = examples.list(GraphQlEngineTest.SHOP);

    // Then
    assertThat(operations).extracting(ExampleOperations.Operation::operationName)
        .containsExactly("User", "Users", "Node", "Search", "Secret", "CreateUser");
    assertThat(operations).allSatisfy(o -> {
      Map<String, Object> result = engine.execute(GraphQlEngineTest.SHOP, o.query(), o.operationName(), o.variables());
      assertThat(result).as(o.query() + " -> " + result.get("errors")).doesNotContainKey("errors");
    });
  }

  @Test
  void declaresRequiredArgumentsAsVariablesWithExampleValues() {
    // When
    ExampleOperations.Operation create = examples.list(GraphQlEngineTest.SHOP).get(5);

    // Then
    assertThat(create.kind()).isEqualTo("mutation");
    assertThat(create.query()).startsWith("mutation CreateUser($input: NewUser!) {\n  createUser(input: $input) {\n    id\n    name");
    assertThat(create.variables()).containsKey("input");
    assertThat((Map<String, Object>) create.variables().get("input")).containsKeys("name", "email");
  }
}
