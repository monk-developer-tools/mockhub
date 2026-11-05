package io.mockhub.controller;

import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Response;
import io.mockhub.grpc.GrpcCatalog;
import io.mockhub.grpc.GrpcException;
import io.mockhub.grpc.GrpcViews;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;

public final class GrpcController implements ApiController {
  private final ProjectService projects;
  private final GrpcCatalog catalog;

  public GrpcController(ProjectService projects, GrpcCatalog catalog) {
    this.projects = projects;
    this.catalog = catalog;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects/{id}/grpc", ctx -> GrpcViews.project(catalog.setsOf(projects.get(ctx.path("id")).id())));
    r.post("/projects/{id}/grpc/descriptors", ctx -> {
      Project project = projects.get(ctx.path("id"));
      try {
        catalog.add(project.id(), ctx.query("name"), ctx.bytes());
      } catch (GrpcException e) {
        throw ApiException.badRequest(e.getMessage());
      }
      return Response.json(201, GrpcViews.project(catalog.setsOf(project.id())));
    });
    r.delete("/projects/{id}/grpc/descriptors", ctx -> {
      Project project = projects.get(ctx.path("id"));
      String name = ctx.query("name");
      if (!catalog.remove(project.id(), name)) throw ApiException.notFound("Descriptor " + name);
      return null;
    });
  }
}
