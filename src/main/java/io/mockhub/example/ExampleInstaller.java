package io.mockhub.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import io.mockhub.data.CrudResource;
import io.mockhub.data.CrudService;
import io.mockhub.data.CrudStubs;
import io.mockhub.files.BodyFiles;
import io.mockhub.importer.ImporterRegistry;
import io.mockhub.importer.StubImporter;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubMetadata;
import io.mockhub.stub.StubService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class ExampleInstaller {

  private final ExampleCatalog catalog;
  private final ProjectService projects;
  private final StubService stubs;
  private final ImporterRegistry importers;
  private final CrudService crud;
  private final BodyFiles files;

  public ExampleInstaller(ExampleCatalog catalog, ProjectService projects, StubService stubs, ImporterRegistry importers,
      CrudService crud, BodyFiles files) {
    this.catalog = catalog;
    this.projects = projects;
    this.stubs = stubs;
    this.importers = importers;
    this.crud = crud;
    this.files = files;
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
    Set<String> fileNames = saveFiles(project, content.path("files"));
    for (JsonNode resource : content.path("crud")) toCreate.add(crudStub(project, resource));
    content.path("stubs").forEach(s -> toCreate.add(withFiles(s.deepCopy(), project, fileNames)));
    toCreate.forEach(s -> stubs.create(project.id(), s));
    return project;
  }

  private Set<String> saveFiles(Project project, JsonNode declared) {
    Set<String> names = new TreeSet<>();
    for (JsonNode f : declared) {
      String name = f.get("name").asText();
      files.save(project.id(), name, catalog.file(f.get("file").asText()).getBytes(StandardCharsets.UTF_8));
      names.add(name);
    }
    return names;
  }

  private ObjectNode crudStub(Project project, JsonNode declared) {
    CrudResource resource = new CrudResource(declared.path("resource").asText(), declared.path("idField").asText("id"),
        CrudResource.IdType.parse(declared.path("idType").asText("uuid")));
    crud.seed(project.id(), resource, declared.get("seed"));
    ObjectNode stub = CrudStubs.stub(resource);
    if (declared.hasNonNull("description")) StubMetadata.node(stub).put("description", declared.get("description").asText());
    return stub;
  }

  private static ObjectNode withFiles(ObjectNode stub, Project project, Set<String> fileNames) {
    if (stub.path("response") instanceof ObjectNode response && fileNames.contains(response.path("bodyFileName").asText())) {
      response.put("bodyFileName", BodyFiles.bodyFileName(project.id(), response.get("bodyFileName").asText()));
    }
    return stub;
  }
}
