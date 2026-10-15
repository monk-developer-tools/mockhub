package io.mockhub.ai;

import java.util.Map;

public interface AiProvider {
  String id();

  String label();

  String defaultBaseUrl();

  String defaultModel();

  boolean requiresApiKey();

  Map<String, String> extraHeaders(String publicUrl);

  default String baseUrl(AiSettings settings) {
    return settings.baseUrl().isEmpty() ? defaultBaseUrl() : settings.baseUrl();
  }

  default String model(AiSettings settings) {
    return settings.model().isEmpty() ? defaultModel() : settings.model();
  }
}
