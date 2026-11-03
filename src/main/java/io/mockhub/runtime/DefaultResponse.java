package io.mockhub.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;

public record DefaultResponse(int status, Map<String, String> headers, String body, boolean templated) {
  static final int MAX_BODY = 1024 * 1024;
  static final int MAX_HEADERS = 50;

  public DefaultResponse {
    headers = headers == null ? Map.of() : Map.copyOf(headers);
    body = body == null ? "" : body;
  }

  static DefaultResponse parse(JsonNode node) {
    if (Fields.absent(node)) return null;
    int status = Fields.integer(node, "status", 404, 100, 599, "The default response status");
    Map<String, String> headers = new LinkedHashMap<>();
    JsonNode given = node.path("headers");
    if (given.isObject()) {
      given.fields().forEachRemaining(e -> {
        String name = e.getKey().trim();
        if (!name.isEmpty()) headers.put(name, e.getValue().asText(""));
      });
    }
    if (headers.size() > MAX_HEADERS) throw ApiException.badRequest("The default response can have at most " + MAX_HEADERS + " headers");
    String body = node.path("body").asText("");
    if (body.length() > MAX_BODY) throw ApiException.badRequest("The default response body can be at most 1 MB");
    return new DefaultResponse(status, headers, body, node.path("templated").asBoolean(false));
  }
}
