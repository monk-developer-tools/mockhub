package io.mockhub.project;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

public record ApiKey(String header, String value) {
  public static final String DEFAULT_HEADER = "X-API-Key";
  public static final String PLACEHOLDER = "{{apiKey}}";
  private static final Pattern HEADER = Pattern.compile("[A-Za-z0-9!#$%&'*+.^_`|~-]{1,64}");

  public static ApiKey parse(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) return null;
    String value = node.path("value").asText("").trim();
    if (value.isEmpty()) return null;
    String header = node.path("header").asText("").trim();
    if (header.isEmpty()) header = DEFAULT_HEADER;
    if (!HEADER.matcher(header).matches()) throw ApiException.badRequest("'" + header + "' isn't a valid header name");
    if (value.length() > 512) throw ApiException.badRequest("The API key can be at most 512 characters");
    return new ApiKey(header, value);
  }

  public ApiKey hidden() {
    return new ApiKey(header, PLACEHOLDER);
  }

  public boolean isHidden() {
    return PLACEHOLDER.equals(value);
  }

  public boolean accepts(String presented) {
    return presented != null && MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8));
  }
}
