package io.mockhub.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class GraphQlEngine {
  public static final String NO_SUBSCRIPTIONS = "Subscriptions need a WebSocket or SSE connection, which MockHub doesn't serve."
      + " Mock the events with queries, or test the subscription against a real server.";

  private final MockSchemas schemas;

  public GraphQlEngine(MockSchemas schemas) {
    this.schemas = schemas;
  }

  public Map<String, Object> execute(String sdl, String query, String operationName, Map<String, Object> variables) {
    if (subscription(query, operationName)) return error(NO_SUBSCRIPTIONS);
    ExecutionInput input = ExecutionInput.newExecutionInput()
        .query(query)
        .operationName(operationName == null || operationName.isBlank() ? null : operationName)
        .variables(variables == null ? Map.of() : variables)
        .graphQLContext(Map.of(MockWiring.BUDGET, new AtomicInteger()))
        .build();
    ExecutionResult result = schemas.engine(sdl).execute(input);
    return result.toSpecification();
  }

  static boolean subscription(String query, String operationName) {
    if (query == null || query.isBlank()) return false;
    Document document;
    try {
      document = Parser.parse(query);
    } catch (InvalidSyntaxException e) {
      return false;
    }
    List<OperationDefinition> operations = document.getDefinitionsOfType(OperationDefinition.class);
    boolean named = operationName != null && !operationName.isBlank();
    return operations.stream()
        .filter(o -> named ? operationName.equals(o.getName()) : operations.size() == 1)
        .anyMatch(o -> o.getOperation() == OperationDefinition.Operation.SUBSCRIPTION);
  }

  public static Map<String, Object> error(String message) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("errors", List.of(Map.of("message", message)));
    return out;
  }
}
