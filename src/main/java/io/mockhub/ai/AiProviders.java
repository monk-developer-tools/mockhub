package io.mockhub.ai;

import io.mockhub.api.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AiProviders {
  private final Map<String, AiProvider> byId = new LinkedHashMap<>();

  public AiProviders(List<AiProvider> providers) {
    providers.forEach(p -> byId.put(p.id(), p));
  }

  public static AiProviders defaults() {
    return new AiProviders(List.of(new OpenRouterProvider(), new HuggingFaceProvider(), new LiteLlmProvider(), new OpenAiCompatibleProvider()));
  }

  public AiProvider get(String id) {
    AiProvider p = byId.get(id);
    if (p == null) throw ApiException.badRequest("Unknown AI provider '" + id + "'");
    return p;
  }

  public List<AiProvider> all() {
    return List.copyOf(byId.values());
  }
}
