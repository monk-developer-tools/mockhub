package io.mockhub.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.ai.AiException;
import io.mockhub.ai.AiProvider;
import io.mockhub.ai.AiProviders;
import io.mockhub.ai.AiSettings;
import io.mockhub.ai.AiSettingsStore;
import io.mockhub.ai.AssistantService;
import io.mockhub.ai.ChatCompletions;
import io.mockhub.ai.ProposedChange;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import java.util.List;

public final class AiController implements ApiController {
  private static final int MAX_MODELS = 400;

  private final AiSettingsStore store;
  private final AiProviders providers;
  private final ChatCompletions chat;
  private final AssistantService assistant;
  private final ProjectService projects;
  private final BaseUrls urls;

  public AiController(AiSettingsStore store, AiProviders providers, ChatCompletions chat, AssistantService assistant,
      ProjectService projects, BaseUrls urls) {
    this.store = store;
    this.providers = providers;
    this.chat = chat;
    this.assistant = assistant;
    this.projects = projects;
    this.urls = urls;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/ai/status", ctx -> {
      AiSettings s = store.get();
      AiProvider p = providers.get(s.provider());
      return Jsons.object().put("enabled", s.enabled()).put("provider", p.id()).put("providerLabel", p.label()).put("model", p.model(s));
    });

    r.get("/ai/settings", ctx -> view());
    r.put("/ai/settings", ctx -> {
      AiSettings next = parse(ctx.json(), store.get());
      AiProvider p = providers.get(next.provider());
      if (next.enabled() && p.model(next).isEmpty()) throw ApiException.badRequest("Choose a model before turning the assistant on");
      if (next.enabled() && p.requiresApiKey() && next.apiKey().isEmpty()) throw ApiException.badRequest(p.label() + " needs an API key");
      store.save(next);
      return view();
    });
    r.post("/ai/test", ctx -> {
      AiSettings candidate = parse(ctx.json(), store.get());
      AiProvider p = providers.get(candidate.provider());
      try {
        List<String> models = chat.models(candidate);
        ObjectNode out = Jsons.object().put("ok", true).put("modelCount", models.size());
        String model = p.model(candidate);
        out.put("modelFound", model.isEmpty() || models.isEmpty() || models.contains(model));
        ArrayNode list = out.putArray("models");
        models.stream().sorted().limit(MAX_MODELS).forEach(list::add);
        return out;
      } catch (AiException e) {
        return Jsons.object().put("ok", false).put("error", e.getMessage());
      }
    });

    r.post("/projects/{id}/ai/chat", ctx -> {
      Project project = projects.get(ctx.path("id"));
      JsonNode body = ctx.json();
      List<AssistantService.Message> history = Jsons.MAPPER.convertValue(body.path("messages"), new TypeReference<>() {});
      List<ProposedChange> pending = changes(body.path("pending"));
      try {
        return Jsons.MAPPER.valueToTree(assistant.chat(project, urls.mock(ctx, project), history == null ? List.of() : history, pending));
      } catch (AiException e) {
        throw new ApiException(502, e.getMessage());
      }
    });
    r.post("/projects/{id}/ai/apply", ctx -> {
      Project project = projects.get(ctx.path("id"));
      List<AssistantService.Applied> results = assistant.apply(project.id(), changes(ctx.json().path("changes")));
      ObjectNode out = Jsons.object();
      out.put("applied", results.stream().filter(AssistantService.Applied::ok).count());
      out.set("results", Jsons.MAPPER.valueToTree(results));
      return out;
    });
  }

  private static List<ProposedChange> changes(JsonNode node) {
    if (node == null || !node.isArray()) return List.of();
    List<ProposedChange> list = Jsons.MAPPER.convertValue(node, new TypeReference<>() {});
    return list.stream().filter(c -> c != null && c.id() != null && c.action() != null).toList();
  }

  private ObjectNode view() {
    AiSettings s = store.get();
    ObjectNode node = Jsons.MAPPER.valueToTree(s);
    node.remove("apiKey");
    node.put("apiKeySet", !s.apiKey().isEmpty());
    node.put("apiKeyFromEnvironment", store.keyFromEnvironment());
    ArrayNode list = node.putArray("providers");
    for (AiProvider p : providers.all()) {
      list.addObject().put("id", p.id()).put("label", p.label()).put("defaultBaseUrl", p.defaultBaseUrl())
          .put("defaultModel", p.defaultModel()).put("requiresApiKey", p.requiresApiKey());
    }
    return node;
  }

  private AiSettings parse(JsonNode body, AiSettings current) {
    String provider = body.path("provider").asText(current.provider());
    providers.get(provider);
    String key = body.path("apiKey").asText("");
    boolean clearKey = body.path("clearApiKey").asBoolean(false);
    return new AiSettings(
        body.path("enabled").asBoolean(current.enabled()),
        provider,
        body.path("baseUrl").asText(current.baseUrl()),
        clearKey ? "" : key.isEmpty() ? current.apiKey() : key,
        body.path("model").asText(current.model()),
        body.path("temperature").asDouble(current.temperature()),
        body.path("maxToolRounds").asInt(current.maxToolRounds()));
  }
}
