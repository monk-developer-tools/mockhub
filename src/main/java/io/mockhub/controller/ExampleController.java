package io.mockhub.controller;

import io.mockhub.api.ApiRouter;
import io.mockhub.api.Response;
import io.mockhub.example.ExampleCatalog;
import io.mockhub.example.ExampleInstaller;

public final class ExampleController implements ApiController {

  private final ExampleCatalog catalog;
  private final ExampleInstaller installer;
  private final ProjectViews views;

  public ExampleController(ExampleCatalog catalog, ExampleInstaller installer, ProjectViews views) {
    this.catalog = catalog;
    this.installer = installer;
    this.views = views;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/examples", ctx -> catalog.all());
    r.post("/examples/{id}/install", ctx -> Response.json(201, views.view(ctx, installer.install(ctx.path("id")))));
  }
}
