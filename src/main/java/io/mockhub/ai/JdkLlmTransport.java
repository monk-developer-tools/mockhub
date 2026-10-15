package io.mockhub.ai;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class JdkLlmTransport implements LlmTransport {
  private static final Duration CONNECT = Duration.ofSeconds(10);
  private static final Duration READ = Duration.ofSeconds(180);
  private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(CONNECT).build();

  @Override
  public JsonNode get(String url, Map<String, String> headers) {
    HttpRequest.Builder b = HttpRequest.newBuilder(uri(url)).timeout(Duration.ofSeconds(20)).GET();
    headers.forEach(b::header);
    return send(b.build());
  }

  @Override
  public JsonNode post(String url, Map<String, String> headers, JsonNode body) {
    HttpRequest.Builder b = HttpRequest.newBuilder(uri(url))
        .timeout(READ)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(Jsons.write(body)));
    headers.forEach(b::header);
    return send(b.build());
  }

  private static URI uri(String url) {
    try {
      return URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new AiException("Invalid AI endpoint URL: " + url);
    }
  }

  private JsonNode send(HttpRequest request) {
    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      JsonNode json;
      try {
        json = response.body() == null || response.body().isBlank() ? Jsons.object() : Jsons.MAPPER.readTree(response.body());
      } catch (IOException e) {
        throw new AiException("The AI endpoint answered HTTP " + response.statusCode() + " with something that isn't JSON. Check the base URL.");
      }
      if (response.statusCode() >= 400) {
        JsonNode error = json.path("error");
        String message = error.isTextual() ? error.asText() : error.path("message").asText("HTTP " + response.statusCode());
        throw new AiException("The AI provider refused the request (" + response.statusCode() + "): " + message);
      }
      return json;
    } catch (IOException e) {
      throw new AiException("Cannot reach " + request.uri().getHost() + ": " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AiException("Interrupted while calling the AI provider", e);
    }
  }
}
