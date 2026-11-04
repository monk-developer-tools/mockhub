package io.mockhub.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class GraphQlEngine {
  private final MockSchemas schemas;

  public GraphQlEngine(MockSchemas schemas) {
    this.schemas = schemas;
  }

  public Map<String, Object> execute(String sdl, String query, String operationName, Map<String, Object> variables) {
    ExecutionInput input = ExecutionInput.newExecutionInput()
        .query(query)
        .operationName(operationName == null || operationName.isBlank() ? null : operationName)
        .variables(variables == null ? Map.of() : variables)
        .graphQLContext(Map.of(MockWiring.BUDGET, new AtomicInteger()))
        .build();
    ExecutionResult result = schemas.engine(sdl).execute(input);
    return result.toSpecification();
  }

  public static Map<String, Object> error(String message) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("errors", List.of(Map.of("message", message)));
    return out;
  }
}
