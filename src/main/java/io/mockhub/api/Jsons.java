package io.mockhub.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.common.Json;

public final class Jsons {
  public static final ObjectMapper MAPPER =
      new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .enable(SerializationFeature.INDENT_OUTPUT);

  private Jsons() {}

  public static JsonNode fromWireMock(Object o) {
    try {
      return MAPPER.readTree(Json.write(o));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static <T> T toWireMock(JsonNode node, Class<T> type) {
    try {
      return Json.read(MAPPER.writeValueAsString(node), type);
    } catch (Exception e) {
      throw ApiException.badRequest("Invalid WireMock definition: " + rootMessage(e));
    }
  }

  public static ObjectNode object() {
    return MAPPER.createObjectNode();
  }

  public static String write(Object o) {
    try {
      return MAPPER.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String rootMessage(Throwable t) {
    while (t.getCause() != null && t.getCause() != t) t = t.getCause();
    return t.getMessage();
  }
}
