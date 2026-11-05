package io.mockhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.mockhub.ai.AiException;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.api.Response;
import io.mockhub.contract.ApiContract;
import io.mockhub.contract.Contract;
import io.mockhub.contract.ContractDocument;
import io.mockhub.contract.ContractPatcher;
import io.mockhub.contract.DriftService;
import io.mockhub.contract.DriftState;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ContractController implements ApiController {
  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
  private static final Set<String> SOURCES = Set.of("upload", "url", "import", "editor", "ai-patch");

  private final ProjectService projects;
  private final DriftService drift;
  private final ContractPatcher patcher;

  public ContractController(ProjectService projects, DriftService drift, ContractPatcher patcher) {
    this.projects = projects;
    this.drift = drift;
    this.patcher = patcher;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects/{id}/contract", ctx -> view(projects.get(ctx.path("id")).id()));
    r.put("/projects/{id}/contract", ctx -> {
      Project p = projects.get(ctx.path("id"));
      JsonNode body = ctx.json();
      if (body == null) throw ApiException.badRequest("Send {content, source}");
      String source = body.path("source").asText("upload");
      if (!SOURCES.contains(source)) throw ApiException.badRequest("source must be one of " + SOURCES);
      JsonNode spec = body.path("content").isObject() ? ContractDocument.check(body.get("content")) : ContractDocument.parse(body.path("content").asText(""));
      drift.attach(p.id(), spec, source, body.path("fileName").asText(null), body.has("stripPrefix") ? body.path("stripPrefix").asText("") : null,
          ctx.principal());
      return view(p.id());
    });
    r.delete("/projects/{id}/contract", ctx -> {
      drift.detach(projects.get(ctx.path("id")).id());
      return null;
    });
    r.get("/projects/{id}/contract/spec", ctx -> {
      Project p = projects.get(ctx.path("id"));
      Contract contract = drift.contract(p.id()).orElseThrow(() -> new ApiException(404, "This project has no contract"));
      boolean yaml = "yaml".equals(ctx.query("format"));
      try {
        byte[] content = yaml ? YAML.writeValueAsBytes(contract.spec()) : Jsons.write(contract.spec()).getBytes(StandardCharsets.UTF_8);
        return Response.download(p.slug() + "-openapi." + (yaml ? "yaml" : "json"), yaml ? "application/yaml" : "application/json", content);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
    r.put("/projects/{id}/contract/notify", ctx -> {
      Project p = projects.get(ctx.path("id"));
      JsonNode body = ctx.json();
      drift.setNotifyUrl(p.id(), body == null ? null : body.path("url").asText(null));
      return view(p.id());
    });
    r.get("/projects/{id}/drift", ctx -> driftView(projects.get(ctx.path("id")).id()));
    r.post("/projects/{id}/drift/check", ctx -> {
      Project p = projects.get(ctx.path("id"));
      drift.check(p.id());
      return driftView(p.id());
    });
    r.post("/projects/{id}/drift/recheck", ctx -> {
      Project p = projects.get(ctx.path("id"));
      drift.restart(p.id());
      return driftView(p.id());
    });
    r.put("/projects/{id}/drift/findings/{key}", ctx -> {
      Project p = projects.get(ctx.path("id"));
      JsonNode body = ctx.json();
      drift.setExpected(p.id(), ctx.path("key"), body != null && body.path("expected").asBoolean(false));
      return driftView(p.id());
    });
    r.post("/projects/{id}/drift/patch", ctx -> {
      Project p = projects.get(ctx.path("id"));
      Contract contract = drift.contract(p.id()).orElseThrow(() -> new ApiException(404, "This project has no contract"));
      JsonNode body = ctx.json();
      Set<String> keys = new HashSet<>();
      if (body != null) body.path("keys").forEach(k -> keys.add(k.asText()));
      List<DriftState.Finding> chosen = drift.state(p.id()).findings().stream()
          .filter(f -> keys.isEmpty() ? !drift.state(p.id()).expected().contains(f.key()) : keys.contains(f.key()))
          .toList();
      try {
        ContractPatcher.Suggestion s = patcher.suggest(p.id(), p.basePath(), contract, chosen);
        ObjectNode out = Jsons.object();
        out.put("summary", s.summary());
        out.set("operations", s.operations());
        out.put("before", Jsons.write(contract.spec()));
        out.put("after", Jsons.write(s.spec()));
        return out;
      } catch (AiException e) {
        throw new ApiException(502, e.getMessage());
      }
    });
  }

  private ObjectNode view(String projectId) {
    Optional<Contract> found = drift.contract(projectId);
    ObjectNode out = Jsons.object();
    out.put("attached", found.isPresent());
    if (found.isEmpty()) return out;
    Contract c = found.get();
    ApiContract api = c.compile();
    out.put("title", ContractDocument.title(c.spec()));
    out.put("version", ContractDocument.version(c.spec()));
    out.put("dialect", ContractDocument.dialect(c.spec()));
    out.put("source", c.source());
    out.put("fileName", c.fileName());
    out.put("updatedAt", c.updatedAt());
    out.put("updatedBy", c.updatedBy());
    out.put("notifyUrl", c.notifyUrl());
    out.put("stripPrefix", c.stripPrefix());
    ArrayNode prefixes = out.putArray("prefixes");
    api.prefixes().forEach(prefixes::add);
    ArrayNode ops = out.putArray("operations");
    api.operations().stream().sorted((a, b) -> a.path().equals(b.path()) ? a.method().compareTo(b.method()) : a.path().compareTo(b.path()))
        .forEach(op -> ops.addObject().put("method", op.method()).put("path", op.path()));
    return out;
  }

  private ObjectNode driftView(String projectId) {
    ObjectNode out = Jsons.object();
    boolean attached = drift.contract(projectId).isPresent();
    out.put("attached", attached);
    DriftState state = attached ? drift.state(projectId) : DriftState.empty();
    out.put("lastCheckedAt", state.lastCheckedAt());
    out.put("scanned", state.scanned());
    out.put("capped", state.capped());
    if (state.lastNotice() != null) out.set("lastNotice", Jsons.MAPPER.valueToTree(state.lastNotice()));
    ArrayNode findings = out.putArray("findings");
    int open = 0;
    for (DriftState.Finding f : state.findings()) {
      boolean expected = state.expected().contains(f.key());
      if (!expected) open++;
      ObjectNode node = findings.addObject();
      node.put("key", f.key());
      node.put("kind", f.kind().name());
      node.put("label", f.kind().label());
      node.put("side", f.kind().side());
      node.put("method", f.method());
      node.put("path", f.path());
      node.put("detail", f.detail());
      node.put("count", f.count());
      node.put("firstSeen", f.firstSeen());
      node.put("lastSeen", f.lastSeen());
      node.put("expected", expected);
      node.putObject("example").put("id", f.exampleId()).put("url", f.exampleUrl()).put("status", f.exampleStatus());
    }
    out.put("open", open);
    out.put("expected", state.findings().size() - open);
    return out;
  }
}
