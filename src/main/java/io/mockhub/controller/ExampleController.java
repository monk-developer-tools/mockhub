package io.mockhub.controller;

import io.mockhub.api.ApiRouter;
import io.mockhub.api.Response;
import io.mockhub.example.ExampleCatalog;
import io.mockhub.example.ExampleInstaller;
import io.mockhub.team.Team;

public final class ExampleController implements ApiController {

  private final ExampleCatalog catalog;
  private final ExampleInstaller installer;
  private final ProjectViews views;
  private final TeamChoice teamChoice;

  public ExampleController(ExampleCatalog catalog, ExampleInstaller installer, ProjectViews views, TeamChoice teamChoice) {
    this.catalog = catalog;
    this.installer = installer;
    this.views = views;
    this.teamChoice = teamChoice;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/examples", ctx -> catalog.all());
    r.post("/examples/{id}/install", ctx -> {
      Team team = teamChoice.writable(ctx.principal(), ctx.query("orgId"), ctx.query("teamId"));
      return Response.json(201, views.view(ctx, installer.install(ctx.path("id"), team.orgId(), team.id())));
    });
  }
}
