package io.mockhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.ai.AssistantService;
import io.mockhub.ai.StagedChanges;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import java.util.ArrayList;
import java.util.List;

public final class StagedController implements ApiController {
  private final ProjectService projects;
  private final StagedChanges staged;
  private final AssistantService assistant;

  public StagedController(ProjectService projects, StagedChanges staged, AssistantService assistant) {
    this.projects = projects;
    this.staged = staged;
    this.assistant = assistant;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects/{id}/staged", ctx -> Jsons.MAPPER.valueToTree(staged.list(projects.get(ctx.path("id")).id())));
    r.post("/projects/{id}/staged/apply", ctx -> {
      Project project = projects.get(ctx.path("id"));
      List<String> ids = ids(ctx.json().path("ids"));
      List<AssistantService.Applied> results = assistant.apply(project.id(), staged.take(project.id(), ids), ctx.principal().label());
      staged.remove(project.id(), results.stream().filter(AssistantService.Applied::ok).map(AssistantService.Applied::changeId).toList());
      ObjectNode out = Jsons.object();
      out.put("applied", results.stream().filter(AssistantService.Applied::ok).count());
      out.set("results", Jsons.MAPPER.valueToTree(results));
      return out;
    });
    r.delete("/projects/{id}/staged/{changeId}", ctx -> {
      Project project = projects.get(ctx.path("id"));
      String changeId = ctx.path("changeId");
      staged.find(project.id(), changeId).orElseThrow(() -> ApiException.notFound("Staged change " + changeId + " not found"));
      staged.remove(project.id(), List.of(changeId));
      return null;
    });
  }

  private static List<String> ids(JsonNode node) {
    if (!node.isArray() || node.isEmpty()) throw ApiException.badRequest("Send the ids of the staged changes to apply");
    List<String> ids = new ArrayList<>();
    node.forEach(n -> ids.add(n.asText()));
    return ids;
  }
}
