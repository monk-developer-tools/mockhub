package io.mockhub.ai;

import java.util.Map;

public final class LiteLlmProvider implements AiProvider {
  public static final String ID = "litellm";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String label() {
    return "LiteLLM proxy";
  }

  @Override
  public String defaultBaseUrl() {
    return "http://localhost:4000";
  }

  @Override
  public String defaultModel() {
    return "";
  }

  @Override
  public boolean requiresApiKey() {
    return false;
  }

  @Override
  public Map<String, String> extraHeaders(String publicUrl) {
    return Map.of();
  }
}
