package io.mockhub.importer;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.graphql.ExampleOperations;
import io.mockhub.graphql.GraphQlException;
import io.mockhub.graphql.GraphQlTransformer;
import io.mockhub.graphql.SchemaSource;
import java.util.List;

public final class GraphQlStubImporter implements StubImporter {
  public static final String DEFAULT_PATH = "/graphql";

  private final ExampleOperations examples;

  public GraphQlStubImporter(ExampleOperations examples) {
    this.examples = examples;
  }

  @Override
  public String format() {
    return "graphql";
  }

  @Override
  public Result parse(Request request) {
    String path = request.option("path") == null ? DEFAULT_PATH : request.option("path").trim();
    if (!path.startsWith("/")) path = "/" + path;
    String name = request.option("name") == null ? "GraphQL API" : request.option("name").trim();
    String sdl;
    List<ExampleOperations.Operation> operations;
    try {
      sdl = SchemaSource.toSdl(request.body());
      operations = examples.list(sdl);
    } catch (GraphQlException e) {
      throw ApiException.badRequest(e.getMessage());
    }
    ObjectNode details = Jsons.object().put("endpointPath", path).put("service", name);
    ArrayNode ops = details.putArray("operations");
    operations.forEach(o -> ops.add(Jsons.object().put("kind", o.kind()).put("field", o.field()).put("operationName", o.operationName())));
    return new Result(List.of(endpoint(name, path, sdl)), details);
  }

  public static ObjectNode endpoint(String name, String path, String sdl) {
    ObjectNode stub = Jsons.object();
    stub.put("name", "GraphQL: " + name);
    stub.put("priority", 10);
    stub.putObject("request").put("method", "ANY").put("urlPath", path);
    ObjectNode response = stub.putObject("response");
    response.put("status", 200);
    response.putObject("headers").put("Content-Type", "application/json");
    response.putArray("transformers").add(GraphQlTransformer.NAME);
    response.putObject("transformerParameters").put(GraphQlTransformer.SCHEMA, sdl);
    ObjectNode meta = stub.putObject("metadata").putObject("mockhub");
    meta.put("type", "graphql").put("folder", "GraphQL")
        .put("description", "Answers every query and mutation from the schema with generated data. Add operation stubs to fix answers for an operation.");
    return stub;
  }
}
