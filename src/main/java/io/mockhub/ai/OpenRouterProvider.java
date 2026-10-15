package io.mockhub.ai;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OpenRouterProvider implements AiProvider {
  public static final String ID = "openrouter";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String label() {
    return "OpenRouter";
  }

  @Override
  public String defaultBaseUrl() {
    return "https://openrouter.ai/api/v1";
  }

  @Override
  public String defaultModel() {
    return "openrouter/auto";
  }

  @Override
  public boolean requiresApiKey() {
    return true;
  }

  @Override
  public Map<String, String> extraHeaders(String publicUrl) {
    Map<String, String> headers = new LinkedHashMap<>();
    if (publicUrl != null && !publicUrl.isBlank()) headers.put("HTTP-Referer", publicUrl);
    headers.put("X-Title", "MockHub");
    return headers;
  }
}
