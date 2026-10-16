package io.mockhub.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import io.mockhub.importer.ImporterRegistry;
import io.mockhub.importer.StubImporter;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExampleInstaller {

  private final ExampleCatalog catalog;
  private final ProjectService projects;
  private final StubService stubs;
  private final ImporterRegistry importers;

  public ExampleInstaller(ExampleCatalog catalog, ProjectService projects, StubService stubs, ImporterRegistry importers) {
    this.catalog = catalog;
    this.projects = projects;
    this.stubs = stubs;
    this.importers = importers;
  }

  public Project install(String exampleId, String orgId, String teamId) {
    Example example = catalog.get(exampleId);
    JsonNode content = catalog.content(example);
    ObjectNode request = Jsons.object()
        .put("name", example.name())
        .put("slug", projects.availableSlug(example.slug()))
        .put("description", example.description())
        .put("orgId", orgId)
        .put("teamId", teamId);
    Project project = projects.create(request);

    List<ObjectNode> toCreate = new ArrayList<>();
    for (JsonNode imp : content.path("imports")) {
      StubImporter importer = importers.get(imp.get("format").asText());
      Map<String, String> options = new LinkedHashMap<>();
      imp.path("options").fields().forEachRemaining(e -> options.put(e.getKey(), e.getValue().asText()));
      toCreate.addAll(importer.parse(new StubImporter.Request(project, catalog.file(imp.get("file").asText()), options)).stubs());
    }
    content.path("stubs").forEach(s -> toCreate.add(s.deepCopy()));
    toCreate.forEach(s -> stubs.create(project.id(), s));
    return project;
  }
}
