package io.mockhub.ai;

import java.util.Map;

public final class OpenAiCompatibleProvider implements AiProvider {
  public static final String ID = "openai";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String label() {
    return "Other OpenAI-compatible API";
  }

  @Override
  public String defaultBaseUrl() {
    return "https://api.openai.com/v1";
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
