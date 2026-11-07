package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.data.CrudResource;
import io.mockhub.data.CrudStubs;
import io.mockhub.example.ExampleInstaller;
import io.mockhub.importer.StubImporter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class InstantSources {
  static final int MAX_RECORDS = 50;
  static final int MAX_SPEC = 500 * 1024;
  static final int MAX_NAME = 60;
  private static final Map<String, String> LABELS = Map.of("openapi", "OpenAPI or Swagger file", "wsdl", "WSDL file", "graphql", "GraphQL schema");

  private InstantSources() {}

  static InstantSource stubs(List<JsonNode> stubs) {
    return project -> InstantSeed.exactly(stubs);
  }

  static InstantSource imported(StubImporter importer, String text) {
    if (text == null || text.isBlank()) throw new InstantStubRules.Refused("Paste, upload or link the " + LABELS.getOrDefault(importer.format(), importer.format()));
    if (text.length() > MAX_SPEC) throw new InstantStubRules.Refused("Keep the file under 500 KB");
    return project -> {
      StubImporter.Result result = importer.parse(new StubImporter.Request(project, text, Map.of("limit", String.valueOf(InstantMocks.MAX_STUBS))));
      int operations = result.details().path("operations").size();
      int leftOut = importer.format().equals("openapi") ? Math.max(0, operations - result.stubs().size()) : 0;
      return new InstantSeed(new ArrayList<>(result.stubs()), List.of(), false, leftOut);
    };
  }

  static InstantSource example(ExampleInstaller installer, String exampleId) {
    return project -> {
      ExampleInstaller.Plan plan = installer.plan(exampleId, project);
      if (plan.usesFiles()) throw new InstantStubRules.Refused("That example needs files, which instant mocks don't keep");
      return new InstantSeed(new ArrayList<>(plan.stubs()), plan.seeds(), false, 0);
    };
  }

  static InstantSource records(String resourceName, JsonNode records) {
    if (records == null || !records.isArray() || records.isEmpty()) throw new InstantStubRules.Refused("Send records as a JSON list of objects");
    if (records.size() > MAX_RECORDS) throw new InstantStubRules.Refused("Start with up to " + MAX_RECORDS + " records");
    for (JsonNode r : records) {
      if (!r.isObject()) throw new InstantStubRules.Refused("Each record must be a JSON object");
    }
    String name = resourceName == null ? "" : resourceName.trim();
    if (name.length() > MAX_NAME) throw new InstantStubRules.Refused("Keep the resource name under " + MAX_NAME + " characters");
    CrudResource resource;
    try {
      resource = new CrudResource(name, "id", idType(records));
    } catch (RuntimeException e) {
      throw new InstantStubRules.Refused("Name the resource with lowercase letters, digits and dashes, like users or order-items");
    }
    return project -> new InstantSeed(List.of(CrudStubs.stub(resource)), List.of(new ExampleInstaller.Seed(resource, records)), false, 0);
  }

  private static CrudResource.IdType idType(JsonNode records) {
    for (JsonNode r : records) {
      JsonNode id = r.get("id");
      if (id != null && !id.isNull() && !id.canConvertToLong() && !id.asText().matches("\\d+")) return CrudResource.IdType.UUID;
    }
    return CrudResource.IdType.NUMBER;
  }
}
