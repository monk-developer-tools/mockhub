package io.mockhub.ai;

import java.util.Map;

public final class HuggingFaceProvider implements AiProvider {
  public static final String ID = "huggingface";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String label() {
    return "Hugging Face";
  }

  @Override
  public String defaultBaseUrl() {
    return "https://router.huggingface.co/v1";
  }

  @Override
  public String defaultModel() {
    return "openai/gpt-oss-120b";
  }

  @Override
  public boolean requiresApiKey() {
    return true;
  }

  @Override
  public Map<String, String> extraHeaders(String publicUrl) {
    return Map.of();
  }
}
