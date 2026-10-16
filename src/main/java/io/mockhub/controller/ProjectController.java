package io.mockhub.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.api.Response;
import io.mockhub.auth.Principal;
import io.mockhub.auth.Role;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.team.Team;

public final class ProjectController implements ApiController {

  private final ProjectService projects;
  private final ProjectViews views;
  private final TeamChoice teamChoice;

  public ProjectController(ProjectService projects, ProjectViews views, TeamChoice teamChoice) {
    this.projects = projects;
    this.views = views;
    this.teamChoice = teamChoice;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects", ctx -> {
      Principal p = ctx.principal();
      String orgId = ctx.query("orgId");
      String teamId = ctx.query("teamId");
      ArrayNode out = Jsons.MAPPER.createArrayNode();
      projects.findAll().stream()
          .filter(project -> p.canInTeam(project.orgId(), project.teamId(), Role.VIEWER))
          .filter(project -> orgId == null || orgId.equals(project.orgId()))
          .filter(project -> teamId == null || teamId.equals(project.teamId()))
          .forEach(project -> out.add(views.view(ctx, project)));
      return out;
    });
    r.post("/projects", ctx -> {
      ObjectNode body = (ObjectNode) ctx.json();
      Team team = teamChoice.writable(ctx.principal(), body.path("orgId").asText(""), body.path("teamId").asText(""));
      body.put("orgId", team.orgId()).put("teamId", team.id());
      return Response.json(201, views.view(ctx, projects.create(body)));
    });
    r.get("/projects/{id}", ctx -> views.view(ctx, projects.get(ctx.path("id"))));
    r.put("/projects/{id}", ctx -> {
      ObjectNode body = (ObjectNode) ctx.json();
      Project current = projects.get(ctx.path("id"));
      String targetTeam = body.path("teamId").asText("");
      String targetOrg = body.path("orgId").asText("");
      boolean moving = (!targetTeam.isBlank() && !targetTeam.equals(current.teamId())) || (!targetOrg.isBlank() && !targetOrg.equals(current.orgId()));
      if (moving) {
        Team team = teamChoice.writable(ctx.principal(), targetOrg, targetTeam);
        body.put("orgId", team.orgId()).put("teamId", team.id());
      } else {
        body.remove("orgId");
        body.remove("teamId");
      }
      return views.view(ctx, projects.update(current.id(), body));
    });
    r.delete("/projects/{id}", ctx -> {
      projects.delete(ctx.path("id"));
      return null;
    });
  }
}
