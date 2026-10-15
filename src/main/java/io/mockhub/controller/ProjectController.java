package io.mockhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.api.Response;
import io.mockhub.auth.Principal;
import io.mockhub.auth.Role;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;

public final class ProjectController implements ApiController {

  private final ProjectService projects;
  private final ProjectViews views;
  private final OrgChoice orgChoice;

  public ProjectController(ProjectService projects, ProjectViews views, OrgChoice orgChoice) {
    this.projects = projects;
    this.views = views;
    this.orgChoice = orgChoice;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects", ctx -> {
      Principal p = ctx.principal();
      String orgId = ctx.query("orgId");
      ArrayNode out = Jsons.MAPPER.createArrayNode();
      projects.findAll().stream()
          .filter(project -> p.can(project.orgId(), Role.VIEWER))
          .filter(project -> orgId == null || orgId.equals(project.orgId()))
          .forEach(project -> out.add(views.view(ctx, project)));
      return out;
    });
    r.post("/projects", ctx -> {
      ObjectNode body = (ObjectNode) ctx.json();
      body.put("orgId", orgChoice.writable(ctx.principal(), body.path("orgId").asText("")));
      return Response.json(201, views.view(ctx, projects.create(body)));
    });
    r.get("/projects/{id}", ctx -> views.view(ctx, projects.get(ctx.path("id"))));
    r.put("/projects/{id}", ctx -> {
      JsonNode body = ctx.json();
      Project current = projects.get(ctx.path("id"));
      String target = body.path("orgId").asText("");
      if (!target.isBlank() && !target.equals(current.orgId())) orgChoice.writable(ctx.principal(), target);
      return views.view(ctx, projects.update(current.id(), body));
    });
    r.delete("/projects/{id}", ctx -> {
      projects.delete(ctx.path("id"));
      return null;
    });
  }
}
