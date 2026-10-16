package io.mockhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Ctx;
import io.mockhub.api.Jsons;
import io.mockhub.api.Response;
import io.mockhub.data.CrudResource;
import io.mockhub.data.CrudService;
import io.mockhub.data.CrudStubs;
import io.mockhub.data.DataStore;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubService;
import java.util.Map;

public final class DataController implements ApiController {
  private final ProjectService projects;
  private final StubService stubs;
  private final DataStore store;
  private final CrudService crud;

  public DataController(ProjectService projects, StubService stubs, DataStore store, CrudService crud) {
    this.projects = projects;
    this.stubs = stubs;
    this.store = store;
    this.crud = crud;
  }

  @Override
  public void register(ApiRouter r) {
    r.post("/projects/{id}/crud", ctx -> {
      Project project = projects.get(ctx.path("id"));
      JsonNode body = ctx.json();
      CrudResource resource = new CrudResource(body.path("resource").asText(""), body.path("idField").asText("id"),
          CrudResource.IdType.parse(body.path("idType").asText("uuid")));
      if (CrudStubs.resources(stubs.stubsOf(project.id())).containsKey(resource.name())) {
        throw new ApiException(409, "This project already has a CRUD resource called " + resource.name());
      }
      ObjectNode stub = stubs.create(project.id(), CrudStubs.stub(resource));
      int seeded = crud.seed(project.id(), resource, body.get("seed"));
      ObjectNode out = Jsons.object().put("seeded", seeded);
      out.set("stub", stub);
      return Response.json(201, out);
    });

    r.get("/projects/{id}/data", ctx -> {
      Project project = projects.get(ctx.path("id"));
      Map<String, CrudResource> declared = CrudStubs.resources(stubs.stubsOf(project.id()));
      Map<String, Integer> stored = store.resources(project.id());
      ArrayNode list = Jsons.MAPPER.createArrayNode();
      declared.values().forEach(res -> list.addObject().put("name", res.name()).put("count", stored.getOrDefault(res.name(), 0))
          .put("idField", res.idField()).put("idType", res.idType().id()).put("crud", true));
      stored.forEach((name, count) -> {
        if (!declared.containsKey(name)) list.addObject().put("name", name).put("count", count).put("idField", "id").put("idType", "uuid").put("crud", false);
      });
      ObjectNode out = Jsons.object();
      out.set("resources", list);
      return out;
    });
    r.delete("/projects/{id}/data", ctx -> {
      store.clearProject(projects.get(ctx.path("id")).id());
      return null;
    });

    r.get("/projects/{id}/data/records", ctx -> {
      String projectId = projects.get(ctx.path("id")).id();
      ArrayNode out = Jsons.MAPPER.createArrayNode();
      store.list(projectId, resourceName(ctx)).forEach(out::add);
      return out;
    });
    r.post("/projects/{id}/data/records", ctx -> {
      Project project = projects.get(ctx.path("id"));
      return toResponse(crud.create(project.id(), resource(project, ctx), ctx.json()), 201);
    });
    r.put("/projects/{id}/data/records", ctx -> {
      Project project = projects.get(ctx.path("id"));
      return toResponse(crud.replace(project.id(), resource(project, ctx), requiredId(ctx), ctx.json()), 200);
    });
    r.delete("/projects/{id}/data/records", ctx -> {
      Project project = projects.get(ctx.path("id"));
      String id = ctx.query("id");
      if (id == null || id.isBlank()) {
        store.clear(project.id(), resourceName(ctx));
        return null;
      }
      if (!crud.delete(project.id(), resource(project, ctx), id)) throw ApiException.notFound("Record " + id);
      return null;
    });
  }

  private CrudResource resource(Project project, Ctx ctx) {
    String name = resourceName(ctx);
    CrudResource declared = CrudStubs.resources(stubs.stubsOf(project.id())).get(name);
    return declared != null ? declared : new CrudResource(name, "id", CrudResource.IdType.UUID);
  }

  private static String resourceName(Ctx ctx) {
    String name = ctx.query("resource");
    if (name == null || name.isBlank()) throw ApiException.badRequest("Add ?resource=<name>");
    return new CrudResource(name, "id", CrudResource.IdType.UUID).name();
  }

  private static String requiredId(Ctx ctx) {
    String id = ctx.query("id");
    if (id == null || id.isBlank()) throw ApiException.badRequest("Add &id=<record id>");
    return id;
  }

  private static Object toResponse(CrudService.Result result, int okStatus) {
    if (result instanceof CrudService.Ok ok) return Response.json(okStatus, ok.record());
    if (result instanceof CrudService.NotFound nf) throw new ApiException(404, nf.message());
    if (result instanceof CrudService.Conflict c) throw new ApiException(409, c.message());
    throw ApiException.badRequest(((CrudService.Invalid) result).message());
  }
}
