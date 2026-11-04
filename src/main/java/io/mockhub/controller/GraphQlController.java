package io.mockhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.graphql.ExampleOperations;
import io.mockhub.graphql.GraphQlException;
import io.mockhub.graphql.SchemaSource;
import io.mockhub.project.ProjectService;

public final class GraphQlController implements ApiController {
  private final ProjectService projects;
  private final ExampleOperations examples;

  public GraphQlController(ProjectService projects, ExampleOperations examples) {
    this.projects = projects;
    this.examples = examples;
  }

  @Override
  public void register(ApiRouter r) {
    r.post("/projects/{id}/graphql/operations", ctx -> {
      projects.get(ctx.path("id"));
      JsonNode body = ctx.json();
      String schema = body == null ? null : body.path("schema").asText(null);
      try {
        String sdl = SchemaSource.toSdl(schema);
        ObjectNode out = Jsons.object().put("schema", sdl);
        out.set("operations", Jsons.MAPPER.valueToTree(examples.list(sdl)));
        return out;
      } catch (GraphQlException e) {
        throw ApiException.badRequest(e.getMessage());
      }
    });
  }
}
