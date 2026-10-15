package io.mockhub.sso;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface HttpTransport {
  JsonNode getJson(String url);

  JsonNode postForm(String url, Map<String, String> form);
}
