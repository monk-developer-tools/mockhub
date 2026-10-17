package io.mockhub.controller;

import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Response;
import io.mockhub.stub.StubService;

public final class StubController implements ApiController {
  private final StubService stubs;

  public StubController(StubService stubs) {
    this.stubs = stubs;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects/{id}/stubs", ctx -> stubs.list(ctx.path("id")));
    r.post("/projects/{id}/stubs", ctx -> Response.json(201, stubs.create(ctx.path("id"), ctx.json())));
    r.get("/projects/{id}/stubs/{stubId}", ctx -> stubs.get(ctx.path("id"), ctx.path("stubId")));
    r.put("/projects/{id}/stubs/{stubId}", ctx -> stubs.update(ctx.path("id"), ctx.path("stubId"), ctx.json()));
    r.delete("/projects/{id}/stubs/{stubId}", ctx -> {
      stubs.delete(ctx.path("id"), ctx.path("stubId"));
      return null;
    });
    r.put("/projects/{id}/stubs/{stubId}/enabled", ctx ->
        stubs.setEnabled(ctx.path("id"), ctx.path("stubId"), ctx.json().path("enabled").asBoolean(true)));
    r.post("/projects/{id}/stubs/{stubId}/clone", ctx ->
        Response.json(201, stubs.copy(ctx.path("id"), ctx.path("stubId"), ctx.query("target"))));
    r.post("/projects/{id}/stubs/{stubId}/move", ctx -> {
      String target = ctx.query("target");
      if (target == null) throw ApiException.badRequest("Query parameter 'target' is required");
      return stubs.move(ctx.path("id"), ctx.path("stubId"), target);
    });
    r.post("/projects/{id}/adopt/{stubId}", ctx ->
        stubs.adopt(ctx.path("id"), ctx.path("stubId"), Boolean.parseBoolean(ctx.query("prefix", "false"))));
    r.get("/stubs/unassigned", ctx -> stubs.unassigned());
  }
}
