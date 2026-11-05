package io.mockhub.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;

public final class ContractDocument {
  public static final int MAX_CHARS = 5 * 1024 * 1024;
  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

  private ContractDocument() {}

  public static JsonNode parse(String text) {
    if (text == null || text.isBlank()) throw ApiException.badRequest("Send the OpenAPI document");
    if (text.length() > MAX_CHARS) throw ApiException.badRequest("Contracts can be up to 5 MB");
    JsonNode spec;
    try {
      String trimmed = text.strip();
      spec = trimmed.startsWith("{") ? Jsons.MAPPER.readTree(trimmed) : YAML.readTree(trimmed);
    } catch (Exception e) {
      throw ApiException.badRequest("Can't read the OpenAPI document: " + e.getMessage());
    }
    return check(spec);
  }

  public static JsonNode check(JsonNode spec) {
    if (spec == null || !spec.isObject()) throw ApiException.badRequest("The OpenAPI document must be an object");
    String openapi = spec.path("openapi").asText("");
    String swagger = spec.path("swagger").asText("");
    if (!openapi.startsWith("3.") && !swagger.startsWith("2.")) {
      throw ApiException.badRequest("Not an OpenAPI 3 or Swagger 2 document (missing 'openapi' or 'swagger')");
    }
    if (!spec.path("paths").isObject()) throw ApiException.badRequest("The OpenAPI document has no paths");
    return spec;
  }

  public static String title(JsonNode spec) {
    return spec.path("info").path("title").asText("");
  }

  public static String version(JsonNode spec) {
    return spec.path("info").path("version").asText("");
  }

  public static String dialect(JsonNode spec) {
    return spec.has("swagger") ? "Swagger " + spec.path("swagger").asText() : "OpenAPI " + spec.path("openapi").asText();
  }
}
