package io.mockhub.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChatCompletions {
  private final LlmTransport transport;
  private final AiProviders providers;
  private final String publicUrl;

  public ChatCompletions(LlmTransport transport, AiProviders providers, String publicUrl) {
    this.transport = transport;
    this.providers = providers;
    this.publicUrl = publicUrl;
  }

  public record Completion(JsonNode message, String finishReason, int promptTokens, int completionTokens) {}

  public Completion complete(AiSettings settings, ArrayNode messages, ArrayNode tools) {
    AiProvider provider = providers.get(settings.provider());
    String model = provider.model(settings);
    if (model.isEmpty()) throw new AiException("Choose a model in the AI settings first");
    ObjectNode body = Jsons.object();
    body.put("model", model);
    body.set("messages", messages);
    body.put("temperature", settings.temperature());
    if (tools != null && !tools.isEmpty()) {
      body.set("tools", tools);
      body.put("tool_choice", "auto");
    }
    JsonNode response = transport.post(provider.baseUrl(settings) + "/chat/completions", headers(settings, provider), body);
    JsonNode choice = response.path("choices").path(0);
    if (choice.isMissingNode()) {
      throw new AiException("The AI provider returned no answer" + errorSuffix(response));
    }
    JsonNode usage = response.path("usage");
    return new Completion(choice.path("message"), choice.path("finish_reason").asText(""),
        usage.path("prompt_tokens").asInt(0), usage.path("completion_tokens").asInt(0));
  }

  public List<String> models(AiSettings settings) {
    AiProvider provider = providers.get(settings.provider());
    JsonNode response = transport.get(provider.baseUrl(settings) + "/models", headers(settings, provider));
    List<String> ids = new ArrayList<>();
    for (JsonNode m : response.path("data")) ids.add(m.path("id").asText());
    return ids;
  }

  private Map<String, String> headers(AiSettings settings, AiProvider provider) {
    if (provider.requiresApiKey() && settings.apiKey().isEmpty()) {
      throw new AiException(provider.label() + " needs an API key");
    }
    Map<String, String> headers = new LinkedHashMap<>(provider.extraHeaders(publicUrl));
    if (!settings.apiKey().isEmpty()) headers.put("Authorization", "Bearer " + settings.apiKey());
    return headers;
  }

  private static String errorSuffix(JsonNode response) {
    String message = response.path("error").path("message").asText("");
    return message.isEmpty() ? "" : ": " + message;
  }
}
