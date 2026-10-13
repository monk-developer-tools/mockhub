package io.mockhub.stub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectListener;
import io.mockhub.project.ProjectService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class StubService implements ProjectListener {
  private static final Comparator<StubMapping> ORDER =
      Comparator.comparing((StubMapping s) -> s.getPriority() == null ? 5 : s.getPriority())
          .thenComparing(s -> s.getName() == null ? "" : s.getName().toLowerCase());

  private final StubGateway gateway;
  private final Supplier<ProjectService> projects;

  public StubService(StubGateway gateway, Supplier<ProjectService> projects) {
    this.gateway = gateway;
    this.projects = projects;
  }

  public List<StubMapping> stubsOf(String projectId) {
    return gateway.findAll().stream()
        .filter(s -> projectId.equals(StubMetadata.projectId(s)))
        .sorted(ORDER)
        .collect(Collectors.toList());
  }

  public ArrayNode list(String projectId) {
    Project p = projects.get().get(projectId);
    ArrayNode out = Jsons.MAPPER.createArrayNode();
    stubsOf(projectId).forEach(s -> out.add(relative(s, p)));
    return out;
  }

  public ObjectNode get(String projectId, String stubId) {
    Project p = projects.get().get(projectId);
    return relative(find(p, stubId), p);
  }

  public ArrayNode unassigned() {
    Set<String> ids = projects.get().findAll().stream().map(Project::id).collect(Collectors.toSet());
    ArrayNode out = Jsons.MAPPER.createArrayNode();
    gateway.findAll().stream()
        .filter(s -> StubMetadata.projectId(s) == null || !ids.contains(StubMetadata.projectId(s)))
        .forEach(s -> out.add(Jsons.fromWireMock(s)));
    return out;
  }

  public ObjectNode create(String projectId, JsonNode relativeStub) {
    Project p = projects.get().get(projectId);
    ObjectNode node = toAbsolute(relativeStub, p);
    node.remove("uuid");
    node.put("id", UUID.randomUUID().toString());
    StubMapping stub = Jsons.toWireMock(node, StubMapping.class);
    gateway.add(stub);
    return relative(stub, p);
  }

  public ObjectNode update(String projectId, String stubId, JsonNode relativeStub) {
    Project p = projects.get().get(projectId);
    find(p, stubId);
    ObjectNode node = toAbsolute(relativeStub, p);
    node.put("id", stubId);
    node.put("uuid", stubId);
    StubMapping stub = Jsons.toWireMock(node, StubMapping.class);
    gateway.update(stub);
    return relative(stub, p);
  }

  public void delete(String projectId, String stubId) {
    Project p = projects.get().get(projectId);
    gateway.remove(find(p, stubId).getId());
  }

  public ObjectNode copy(String projectId, String stubId, String targetProjectId) {
    Project p = projects.get().get(projectId);
    ObjectNode copy = relative(find(p, stubId), p);
    String target = targetProjectId == null ? projectId : targetProjectId;
    if (copy.hasNonNull("name") && target.equals(projectId)) {
      copy.put("name", copy.get("name").asText() + " (copy)");
    }
    return create(target, copy);
  }

  public ObjectNode move(String projectId, String stubId, String targetProjectId) {
    ObjectNode moved = copy(projectId, stubId, targetProjectId);
    delete(projectId, stubId);
    return moved;
  }

  public ObjectNode adopt(String projectId, String stubId, boolean prefixUrl) {
    Project p = projects.get().get(projectId);
    StubMapping stub = gateway.findById(parseId(stubId)).orElseThrow(() -> ApiException.notFound("Stub " + stubId));
    ObjectNode node = (ObjectNode) Jsons.fromWireMock(stub);
    if (prefixUrl) StubPaths.toAbsolute(node, p.basePath());
    StubMetadata.node(node).put(StubMetadata.PROJECT_ID, p.id());
    StubMapping updated = Jsons.toWireMock(node, StubMapping.class);
    gateway.update(updated);
    return relative(updated, p);
  }

  public List<ObjectNode> createAll(String projectId, List<? extends JsonNode> stubs) {
    Project p = projects.get().get(projectId);
    List<ObjectNode> created = new ArrayList<>();
    for (JsonNode stub : stubs) {
      ObjectNode node = stub.deepCopy();
      StubPaths.toRelative(node, p.basePath());
      created.add(create(projectId, node));
    }
    return created;
  }

  @Override
  public void onBasePathChanged(Project before, Project after) {
    for (StubMapping stub : stubsOf(before.id())) {
      ObjectNode node = (ObjectNode) Jsons.fromWireMock(stub);
      StubPaths.toRelative(node, before.basePath());
      StubPaths.toAbsolute(node, after.basePath());
      gateway.update(Jsons.toWireMock(node, StubMapping.class));
    }
  }

  @Override
  public void onProjectDeleted(Project project) {
    stubsOf(project.id()).forEach(s -> gateway.remove(s.getId()));
  }

  private StubMapping find(Project p, String stubId) {
    StubMapping stub = gateway.findById(parseId(stubId)).orElse(null);
    if (stub == null || !p.id().equals(StubMetadata.projectId(stub))) {
      throw ApiException.notFound("Stub " + stubId + " in project " + p.slug());
    }
    return stub;
  }

  private static UUID parseId(String id) {
    try {
      return UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("Invalid stub id " + id);
    }
  }

  private static ObjectNode toAbsolute(JsonNode body, Project p) {
    if (body == null || !body.isObject()) throw ApiException.badRequest("Stub must be a JSON object");
    ObjectNode node = body.deepCopy();
    if (!node.has("response")) node.putObject("response").put("status", 200);
    node.remove("insertionIndex");
    StubPaths.toAbsolute(node, p.basePath());
    ObjectNode meta = StubMetadata.node(node);
    meta.put(StubMetadata.PROJECT_ID, p.id());
    if (!meta.has(StubMetadata.TYPE)) meta.put(StubMetadata.TYPE, "rest");
    return node;
  }

  private static ObjectNode relative(StubMapping s, Project p) {
    ObjectNode node = (ObjectNode) Jsons.fromWireMock(s);
    node.remove("insertionIndex");
    StubPaths.toRelative(node, p.basePath());
    return node;
  }
}
