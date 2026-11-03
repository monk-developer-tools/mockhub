package io.mockhub.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.mockhub.ai.MockTools;
import io.mockhub.ai.StagedChanges;
import io.mockhub.api.AdminPaths;
import io.mockhub.api.Jsons;
import io.mockhub.auth.AccessPolicy;
import io.mockhub.auth.Principal;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.ai.ProposedChange;
import java.util.List;
import java.util.function.BiFunction;

public final class ReviewStager implements Stager {
  private final StagedChanges staged;
  private final BiFunction<String, List<ProposedChange>, MockTools> toolsFor;
  private final AccessPolicy policy;

  public ReviewStager(StagedChanges staged, BiFunction<String, List<ProposedChange>, MockTools> toolsFor, AccessPolicy policy) {
    this.staged = staged;
    this.toolsFor = toolsFor;
    this.policy = policy;
  }

  @Override
  public synchronized JsonNode stage(Principal principal, String projectId, String tool, JsonNode args) {
    AccessPolicy.Decision decision = policy.decide(principal, RequestMethod.GET, AdminPaths.API_ROOT + "/projects/" + projectId + "/staged");
    if (!decision.allowed()) throw new McpTools.ToolError(decision.reason());
    MockTools tools = toolsFor.apply(projectId, staged.changes(projectId));
    JsonNode result = parse(tools.execute(tool, args));
    if (result.has("error")) throw new McpTools.ToolError(result.path("error").asText());
    staged.replace(projectId, tools.changes(), principal.label());
    return Jsons.object().put("staged", true)
        .put("message", "Staged for review. Someone with the editor role applies it from the project's Assistant tab.")
        .<ObjectNode>set("change", result);
  }

  private static JsonNode parse(String json) {
    try {
      return Jsons.MAPPER.readTree(json);
    } catch (Exception e) {
      return Jsons.object().put("error", "Could not stage the change");
    }
  }
}
