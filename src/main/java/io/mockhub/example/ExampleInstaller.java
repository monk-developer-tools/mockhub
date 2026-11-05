package io.mockhub.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import io.mockhub.data.CrudResource;
import io.mockhub.data.CrudService;
import io.mockhub.data.CrudStubs;
import io.mockhub.api.ApiException;
import io.mockhub.files.BodyFiles;
import io.mockhub.grpc.GrpcCatalog;
import io.mockhub.grpc.GrpcException;
import io.mockhub.importer.ImporterRegistry;
import io.mockhub.importer.StubImporter;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubMetadata;
import io.mockhub.stub.StubService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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
  private final GrpcCatalog grpc;

  public ExampleInstaller(ExampleCatalog catalog, ProjectService projects, StubService stubs, ImporterRegistry importers,
      CrudService crud, BodyFiles files, GrpcCatalog grpc) {
    this.catalog = catalog;
    this.projects = projects;
    this.stubs = stubs;
    this.importers = importers;
    this.crud = crud;
    this.files = files;
    this.grpc = grpc;
  }

  public record Plan(List<ObjectNode> stubs, List<Seed> seeds, boolean usesFiles, boolean usesDescriptors) {}

  public record Seed(CrudResource resource, JsonNode records) {}

  public Project install(String exampleId, String orgId, String teamId) {
    Example example = catalog.get(exampleId);
    ObjectNode request = Jsons.object()
        .put("name", example.name())
        .put("slug", projects.availableSlug(example.slug()))
        .put("description", example.description())
        .put("orgId", orgId)
        .put("teamId", teamId);
    Project project = projects.create(request);
    addDescriptors(project, catalog.content(example).path("descriptors"));
    Plan plan = plan(exampleId, project);
    Set<String> fileNames = saveFiles(project, catalog.content(example).path("files"));
    plan.seeds().forEach(seed -> crud.seed(project.id(), seed.resource(), seed.records()));
    plan.stubs().forEach(s -> stubs.create(project.id(), withFiles(s, project, fileNames)));
    return project;
  }

  public Plan plan(String exampleId, Project project) {
    JsonNode content = catalog.content(catalog.get(exampleId));
    List<ObjectNode> planned = new ArrayList<>();
    for (JsonNode imp : content.path("imports")) {
      StubImporter importer = importers.get(imp.get("format").asText());
      Map<String, String> options = new LinkedHashMap<>();
      imp.path("options").fields().forEachRemaining(e -> options.put(e.getKey(), e.getValue().asText()));
      planned.addAll(importer.parse(new StubImporter.Request(project, catalog.file(imp.get("file").asText()), options)).stubs());
    }
    List<Seed> seeds = new ArrayList<>();
    for (JsonNode declared : content.path("crud")) {
      CrudResource resource = new CrudResource(declared.path("resource").asText(), declared.path("idField").asText("id"),
          CrudResource.IdType.parse(declared.path("idType").asText("uuid")));
      seeds.add(new Seed(resource, declared.get("seed")));
      ObjectNode stub = CrudStubs.stub(resource);
      if (declared.hasNonNull("description")) StubMetadata.node(stub).put("description", declared.get("description").asText());
      planned.add(stub);
    }
    content.path("stubs").forEach(s -> planned.add(s.deepCopy()));
    return new Plan(planned, seeds, !content.path("files").isEmpty(), !content.path("descriptors").isEmpty());
  }

  private void addDescriptors(Project project, JsonNode declared) {
    try {
      for (JsonNode d : declared) {
        grpc.add(project.id(), d.get("name").asText(), Base64.getMimeDecoder().decode(catalog.file(d.get("file").asText())));
      }
    } catch (GrpcException e) {
      projects.delete(project.id());
      throw new ApiException(409, "This example can't be installed: " + e.getMessage());
    }
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

  private static ObjectNode withFiles(ObjectNode stub, Project project, Set<String> fileNames) {
    if (stub.path("response") instanceof ObjectNode response && fileNames.contains(response.path("bodyFileName").asText())) {
      response.put("bodyFileName", BodyFiles.bodyFileName(project.id(), response.get("bodyFileName").asText()));
    }
    return stub;
  }
}
