package io.mockhub.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.contract.ApiContract;
import io.mockhub.contract.ContractDocument;
import io.mockhub.contract.DriftService;
import io.mockhub.contract.DriftState;
import io.mockhub.data.CrudResource;
import io.mockhub.data.CrudService;
import io.mockhub.data.CrudStubs;
import io.mockhub.grpc.DescriptorNames;
import io.mockhub.grpc.GrpcCatalog;
import io.mockhub.grpc.GrpcException;
import io.mockhub.grpc.ProtoFiles;
import io.mockhub.importer.ImporterRegistry;
import io.mockhub.importer.SpecFetcher;
import io.mockhub.importer.StubImporter;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubService;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class ServerProjectSetup implements ProjectSetup {
  private static final int MAX_FINDINGS = 30;

  private final ProjectService projects;
  private final StubService stubs;
  private final ImporterRegistry importers;
  private final SpecFetcher fetcher;
  private final CrudService crud;
  private final GrpcCatalog grpc;
  private final DriftService drift;

  public ServerProjectSetup(ProjectService projects, StubService stubs, ImporterRegistry importers, SpecFetcher fetcher, CrudService crud,
      GrpcCatalog grpc, DriftService drift) {
    this.projects = projects;
    this.stubs = stubs;
    this.importers = importers;
    this.fetcher = fetcher;
    this.crud = crud;
    this.grpc = grpc;
    this.drift = drift;
  }

  @Override
  public Parsed parse(String projectId, String format, String content) {
    Project project = projects.get(projectId);
    StubImporter importer = importers.get(format);
    try {
      StubImporter.Result result = importer.parse(new StubImporter.Request(project, content, Map.of()));
      return new Parsed(result.stubs(), result.details().path("title").asText(""));
    } catch (ApiException e) {
      throw e;
    } catch (RuntimeException e) {
      throw ApiException.badRequest("Can't read the " + format + " document: " + e.getMessage());
    }
  }

  @Override
  public String fetch(String url) {
    return fetcher.fetch(url);
  }

  @Override
  public JsonNode contract(String content) {
    JsonNode spec = ContractDocument.parse(content);
    ApiContract.of(ContractDocument.check(spec));
    return spec;
  }

  @Override
  public void attachContract(String projectId, JsonNode spec, String fileName, String by) {
    drift.attach(projects.get(projectId).id(), spec, "ai", fileName, null, by);
  }

  @Override
  public ObjectNode checkCrud(String projectId, JsonNode request) {
    CrudResource resource = resource(request);
    if (CrudStubs.resources(stubs.stubsOf(projects.get(projectId).id())).containsKey(resource.name())) {
      throw new ApiException(409, "This project already has a CRUD resource called " + resource.name());
    }
    ObjectNode clean = Jsons.object().put("resource", resource.name()).put("idField", resource.idField()).put("idType", resource.idType().id());
    JsonNode seed = request.path("seed");
    if (!seed.isMissingNode() && !seed.isNull()) {
      if (!seed.isArray()) throw ApiException.badRequest("'seed' must be a list of records");
      clean.set("seed", seed);
    }
    return clean;
  }

  @Override
  public int createCrud(String projectId, JsonNode request) {
    ObjectNode clean = checkCrud(projectId, request);
    CrudResource resource = resource(clean);
    stubs.create(projectId, CrudStubs.stub(resource));
    return crud.seed(projectId, resource, clean.get("seed"));
  }

  @Override
  public void checkProto(String name, String text) {
    try {
      if (!ProtoFiles.isProtoText(DescriptorNames.require(name))) throw ApiException.badRequest("Name the file like library.proto");
      if (ProtoFiles.parse(ProtoFiles.compile(text, name, "")).stream().allMatch(f -> f.getServices().isEmpty())) {
        throw ApiException.badRequest("The .proto defines no services");
      }
    } catch (GrpcException e) {
      throw ApiException.badRequest(e.getMessage());
    }
  }

  @Override
  public void addProto(String projectId, String name, String text) {
    try {
      grpc.add(projects.get(projectId).id(), name, text.getBytes(StandardCharsets.UTF_8));
    } catch (GrpcException e) {
      throw ApiException.badRequest(e.getMessage());
    }
  }

  @Override
  public JsonNode drift(String projectId) {
    String id = projects.get(projectId).id();
    ObjectNode out = Jsons.object();
    boolean attached = drift.contract(id).isPresent();
    out.put("attached", attached);
    if (!attached) return out.put("note", "No contract yet. Import an OpenAPI document with keep_contract or call attach_contract.");
    DriftState state = drift.check(id);
    out.put("lastCheckedAt", state.lastCheckedAt());
    out.put("scanned", state.scanned());
    ArrayNode findings = out.putArray("findings");
    int open = 0;
    for (DriftState.Finding f : state.findings()) {
      boolean expected = state.expected().contains(f.key());
      if (!expected) open++;
      if (findings.size() >= MAX_FINDINGS) continue;
      findings.addObject().put("kind", f.kind().label()).put("method", f.method()).put("path", f.path()).put("detail", f.detail())
          .put("count", f.count()).put("expected", expected);
    }
    out.put("open", open);
    return out;
  }

  private static CrudResource resource(JsonNode request) {
    return new CrudResource(request.path("resource").asText(""), request.path("idField").asText("id"),
        CrudResource.IdType.parse(request.path("idType").asText("uuid")));
  }
}
