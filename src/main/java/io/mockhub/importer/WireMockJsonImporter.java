package io.mockhub.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.stub.StubPaths;
import java.util.ArrayList;
import java.util.List;

public final class WireMockJsonImporter implements StubImporter {
  @Override
  public String format() {
    return "wiremock";
  }

  @Override
  public Result parse(Request request) {
    JsonNode body;
    try {
      body = Jsons.MAPPER.readTree(request.body());
    } catch (Exception e) {
      throw ApiException.badRequest("Invalid JSON: " + e.getMessage());
    }
    JsonNode list = body.isArray() ? body : body.has("mappings") ? body.get("mappings") : Jsons.MAPPER.createArrayNode().add(body);
    String sourceBasePath = body.path("meta").path("mockhubBasePath").asText("");
    List<ObjectNode> stubs = new ArrayList<>();
    for (JsonNode item : list) {
      if (!item.isObject()) throw ApiException.badRequest("Each mapping must be a JSON object");
      ObjectNode stub = item.deepCopy();
      stub.remove("id");
      stub.remove("uuid");
      if (!sourceBasePath.isEmpty()) StubPaths.toRelative(stub, sourceBasePath);
      StubPaths.toRelative(stub, request.project().basePath());
      stubs.add(stub);
    }
    return new Result(stubs, Jsons.object());
  }
}
