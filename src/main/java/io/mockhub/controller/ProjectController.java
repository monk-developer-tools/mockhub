package io.mockhub.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Ctx;
import io.mockhub.api.Jsons;
import io.mockhub.api.Response;
import io.mockhub.journal.RequestLogService;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubMetadata;
import io.mockhub.stub.StubService;
import java.util.List;

public final class ProjectController implements ApiController {
  private final ProjectService projects;
  private final StubService stubs;
  private final RequestLogService requestLog;
  private final BaseUrls urls;

  public ProjectController(ProjectService projects, StubService stubs, RequestLogService requestLog, BaseUrls urls) {
    this.projects = projects;
    this.stubs = stubs;
    this.requestLog = requestLog;
    this.urls = urls;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects", ctx -> {
      ArrayNode out = Jsons.MAPPER.createArrayNode();
      projects.findAll().forEach(p -> out.add(view(ctx, p)));
      return out;
    });
    r.post("/projects", ctx -> Response.json(201, view(ctx, projects.create(ctx.json()))));
    r.get("/projects/{id}", ctx -> view(ctx, projects.get(ctx.path("id"))));
    r.put("/projects/{id}", ctx -> view(ctx, projects.update(ctx.path("id"), ctx.json())));
    r.delete("/projects/{id}", ctx -> {
      projects.delete(ctx.path("id"));
      return null;
    });
  }

  private ObjectNode view(Ctx ctx, Project p) {
    ObjectNode node = Jsons.MAPPER.valueToTree(p);
    List<StubMapping> list = stubs.stubsOf(p.id());
    node.put("basePath", p.basePath());
    node.put("mockUrl", urls.mock(ctx, p));
    node.put("stubCount", list.size());
    node.put("soapStubCount", list.stream().filter(StubMetadata::isSoap).count());
    node.put("requestCount", requestLog.eventsOf(p).size());
    return node;
  }
}
