package io.mockhub.sso;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

public final class JdkHttpTransport implements HttpTransport {
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

  @Override
  public JsonNode getJson(String url) {
    return send(HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build());
  }

  @Override
  public JsonNode postForm(String url, Map<String, String> form) {
    String body = form.entrySet().stream()
        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
        .collect(Collectors.joining("&"));
    return send(HttpRequest.newBuilder(URI.create(url))
        .timeout(TIMEOUT)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build());
  }

  private JsonNode send(HttpRequest request) {
    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      JsonNode json = response.body() == null || response.body().isBlank() ? Jsons.object() : Jsons.MAPPER.readTree(response.body());
      if (response.statusCode() >= 400) {
        String detail = json.path("error_description").asText(json.path("error").asText("HTTP " + response.statusCode()));
        throw new SsoException(detail);
      }
      return json;
    } catch (IOException e) {
      throw new SsoException("Cannot reach " + request.uri().getHost() + ": " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SsoException("Interrupted while calling " + request.uri().getHost(), e);
    }
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
