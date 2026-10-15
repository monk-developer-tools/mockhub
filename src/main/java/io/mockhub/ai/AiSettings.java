package io.mockhub.ai;

public record AiSettings(
    boolean enabled,
    String provider,
    String baseUrl,
    String apiKey,
    String model,
    double temperature,
    int maxToolRounds) {

  public static final int DEFAULT_TOOL_ROUNDS = 8;

  public AiSettings {
    provider = provider == null || provider.isBlank() ? OpenRouterProvider.ID : provider;
    baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
    apiKey = apiKey == null ? "" : apiKey.trim();
    model = model == null ? "" : model.trim();
    temperature = temperature < 0 || temperature > 2 ? 0.2 : temperature;
    maxToolRounds = maxToolRounds <= 0 ? DEFAULT_TOOL_ROUNDS : Math.min(maxToolRounds, 20);
  }

  public static AiSettings disabled() {
    return new AiSettings(false, OpenRouterProvider.ID, "", "", "", 0.2, DEFAULT_TOOL_ROUNDS);
  }

  public AiSettings withApiKey(String key) {
    return new AiSettings(enabled, provider, baseUrl, key, model, temperature, maxToolRounds);
  }
}
