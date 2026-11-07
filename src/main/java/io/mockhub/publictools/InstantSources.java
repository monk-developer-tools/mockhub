package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import io.mockhub.data.CrudResource;
import io.mockhub.data.CrudStubs;
import io.mockhub.example.ExampleInstaller;
import io.mockhub.importer.StubImporter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class InstantSources {
  static final int MAX_RECORDS = 100;
  static final int MAX_SPEC = 500 * 1024;
  static final int MAX_NAME = 60;
  static final int MAX_ROUTES = 20;
  private static final Pattern ROUTE = Pattern.compile("[a-z0-9][a-z0-9-]{0,59}");
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

  static InstantSource json(JsonNode data, String rootName) {
    if (data == null || data.isNull()) throw new InstantStubRules.Refused("Send the JSON as data: an object whose keys become routes, or a list of records");
    if (data.isArray()) return records(rootName == null || rootName.isBlank() ? "items" : rootName, data);
    if (!data.isObject() || data.isEmpty()) throw new InstantStubRules.Refused("Send a JSON object whose keys become routes, like {\"posts\": [{\"id\": 1}]}, or a list of records");
    if (data.size() > MAX_ROUTES) throw new InstantStubRules.Refused("Keep it to " + MAX_ROUTES + " top-level keys; each becomes a route");
    List<JsonNode> stubs = new ArrayList<>();
    List<ExampleInstaller.Seed> seeds = new ArrayList<>();
    List<String> skipped = new ArrayList<>();
    int total = 0;
    for (Map.Entry<String, JsonNode> field : data.properties()) {
      String name = field.getKey();
      JsonNode value = field.getValue();
      if (!ROUTE.matcher(name).matches()) {
        skipped.add(name + ": route names are lowercase letters, digits and dashes, like posts or order-items");
        continue;
      }
      if (isRecords(value)) {
        total += value.size();
        if (total > MAX_RECORDS) throw new InstantStubRules.Refused("Start with up to " + MAX_RECORDS + " records in all");
        for (JsonNode r : value) {
          if (r.toString().getBytes(StandardCharsets.UTF_8).length > InstantRecordPolicy.MAX_RECORD_BYTES) {
            throw new InstantStubRules.Refused("Each record in " + name + " must be under 4 KB");
          }
        }
        CrudResource resource = new CrudResource(name, "id", idType(value));
        stubs.add(CrudStubs.stub(resource));
        seeds.add(new ExampleInstaller.Seed(resource, value));
      } else {
        ObjectNode stub = Jsons.object().put("name", "GET /" + name);
        stub.putObject("request").put("method", "GET").put("urlPath", "/" + name);
        stub.putObject("response").put("status", 200).set("jsonBody", value);
        stubs.add(stub);
      }
    }
    return project -> new InstantSeed(stubs, seeds, false, 0, skipped);
  }

  private static boolean isRecords(JsonNode value) {
    if (!value.isArray()) return false;
    for (JsonNode item : value) {
      if (!item.isObject()) return false;
    }
    return true;
  }

  private static CrudResource.IdType idType(JsonNode records) {
    for (JsonNode r : records) {
      JsonNode id = r.get("id");
      if (id != null && !id.isNull() && !id.canConvertToLong() && !id.asText().matches("\\d+")) return CrudResource.IdType.UUID;
    }
    return CrudResource.IdType.NUMBER;
  }
}
