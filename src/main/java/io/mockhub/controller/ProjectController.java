package io.mockhub.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.api.Response;
import io.mockhub.project.ProjectService;

public final class ProjectController implements ApiController {

  private final ProjectService projects;
  private final ProjectViews views;

  public ProjectController(ProjectService projects, ProjectViews views) {
    this.projects = projects;
    this.views = views;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects", ctx -> {
      ArrayNode out = Jsons.MAPPER.createArrayNode();
      projects.findAll().forEach(p -> out.add(views.view(ctx, p)));
      return out;
    });
    r.post("/projects", ctx -> Response.json(201, views.view(ctx, projects.create(ctx.json()))));
    r.get("/projects/{id}", ctx -> views.view(ctx, projects.get(ctx.path("id"))));
    r.put("/projects/{id}", ctx -> views.view(ctx, projects.update(ctx.path("id"), ctx.json())));
    r.delete("/projects/{id}", ctx -> {
      projects.delete(ctx.path("id"));
      return null;
    });
  }
}
