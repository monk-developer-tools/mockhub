package io.mockhub.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.project.ProjectService;
import io.mockhub.state.StateRecord;
import io.mockhub.state.StateRepository;
import java.time.Clock;
import java.util.Optional;

public final class StateController implements ApiController {
  private final ProjectService projects;
  private final StateRepository state;
  private final Clock clock;

  public StateController(ProjectService projects, StateRepository state, Clock clock) {
    this.projects = projects;
    this.state = state;
    this.clock = clock;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects/{id}/state", ctx -> {
      String projectId = projects.get(ctx.path("id")).id();
      ObjectNode out = Jsons.object();
      ArrayNode contexts = out.putArray("contexts");
      state.list(projectId).forEach(record -> contexts.add(record.toJson()));
      return out;
    });
    r.put("/projects/{id}/state/context", ctx -> {
      String projectId = projects.get(ctx.path("id")).id();
      String name = ctx.query("name");
      long count = Optional.ofNullable(name).flatMap(n -> state.find(n, Optional.of(projectId)))
          .filter(found -> found.projectId().equals(projectId))
          .map(found -> found.record().updateCount() + 1).orElse(1L);
      StateRecord record = StateRecord.edited(name, ctx.json(), count, clock.instant());
      state.save(projectId, record);
      return record.toJson();
    });
    r.delete("/projects/{id}/state/context", ctx -> {
      String projectId = projects.get(ctx.path("id")).id();
      String name = ctx.query("name");
      if (name == null || name.isBlank()) throw ApiException.badRequest("Add ?name=<context name>");
      if (!state.delete(projectId, name)) throw ApiException.notFound("State context " + name);
      return null;
    });
    r.delete("/projects/{id}/state", ctx -> {
      state.clear(projects.get(ctx.path("id")).id());
      return null;
    });
  }
}
