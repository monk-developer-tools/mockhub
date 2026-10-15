package io.mockhub.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface LlmTransport {
  JsonNode get(String url, Map<String, String> headers);

  JsonNode post(String url, Map<String, String> headers, JsonNode body);
}
