package io.mockhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Response;
import io.mockhub.auth.Principal;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubService;
import java.util.ArrayList;
import java.util.List;

public final class StubController implements ApiController {
  private final StubService stubs;
  private final ProjectAccess access;

  public StubController(StubService stubs, ProjectService projects) {
    this.stubs = stubs;
    this.access = new ProjectAccess(projects);
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
        Response.json(201, stubs.copy(ctx.path("id"), ctx.path("stubId"), target(ctx.principal(), ctx.query("target"), ctx.path("id")))));
    r.post("/projects/{id}/stubs/{stubId}/move", ctx -> {
      String target = ctx.query("target");
      if (target == null) throw ApiException.badRequest("Query parameter 'target' is required");
      return stubs.move(ctx.path("id"), ctx.path("stubId"), access.editable(ctx.principal(), target));
    });
    r.post("/projects/{id}/adopt/{stubId}", ctx ->
        stubs.adopt(ctx.path("id"), ctx.path("stubId"), Boolean.parseBoolean(ctx.query("prefix", "false"))));
    r.get("/stubs/unassigned", ctx -> stubs.unassigned());
    r.get("/projects/{id}/matching-order", ctx -> stubs.matchingOrder(ctx.path("id")));
    r.put("/projects/{id}/matching-order", ctx -> stubs.reorder(ctx.path("id"), ids(ctx.json().path("ids"))));
  }

  private static List<String> ids(JsonNode node) {
    if (!node.isArray()) throw ApiException.badRequest("Send the stub ids as an array called 'ids'");
    List<String> ids = new ArrayList<>();
    node.forEach(n -> ids.add(n.asText()));
    return ids;
  }

  private String target(Principal principal, String target, String projectId) {
    return target == null || target.equals(projectId) ? projectId : access.editable(principal, target);
  }
}
