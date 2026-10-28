package io.mockhub.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

final class ApiClient {

  private static final ObjectMapper JSON = new ObjectMapper();
  private final HttpClient http = HttpClient.newHttpClient();
  private final String baseUrl;
  private String authorization;

  ApiClient(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  ApiClient bearer(String token) {
    authorization = "Bearer " + token;
    return this;
  }

  ApiClient login(String user, String password) {
    Result r = new ApiClient(baseUrl).post("/__admin/mockhub/api/auth/login",
        "{\"username\":\"" + user + "\",\"password\":\"" + password + "\"}");
    if (r.status() != 200) throw new IllegalStateException("Login failed: " + r.body());
    return bearer(r.json().get("token").asText());
  }

  ApiClient as(String user, String password) {
    authorization = "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    return this;
  }

  Result get(String path) {
    return send("GET", path, null, null);
  }

  Result post(String path, String body) {
    return send("POST", path, body, "application/json");
  }

  Result postXml(String path, String body) {
    return send("POST", path, body, "text/xml");
  }

  Result put(String path, String body) {
    return send("PUT", path, body, "application/json");
  }

  Result delete(String path) {
    return send("DELETE", path, null, null);
  }

  Result send(String method, String path, String body, String contentType) {
    return send(method, path, body, contentType, Map.of());
  }

  Result send(String method, String path, String body, String contentType, Map<String, String> headers) {
    return send(method, path, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body), contentType, headers);
  }

  Result postBytes(String path, byte[] body, String contentType) {
    return send("POST", path, HttpRequest.BodyPublishers.ofByteArray(body), contentType, Map.of());
  }

  private Result send(String method, String path, HttpRequest.BodyPublisher body, String contentType, Map<String, String> headers) {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path)).method(method, body);
    if (contentType != null) b.header("Content-Type", contentType);
    headers.forEach(b::header);
    if (authorization != null && path.startsWith("/__admin")) b.header("Authorization", authorization);
    try {
      HttpResponse<byte[]> r = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
      return new Result(r.statusCode(), r.body(), r.headers().firstValue("Content-Type").orElse(""),
          r.headers().firstValue("Content-Disposition").orElse(""), r.headers().firstValue("Location").orElse(""),
          r.headers().firstValue("X-MockHub-Simulated-Error").orElse(""));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  record Result(int status, byte[] bytes, String contentType, String contentDisposition, String location, String simulatedError) {
    String body() {
      return new String(bytes, StandardCharsets.UTF_8);
    }

    JsonNode json() {
      try {
        return JSON.readTree(bytes);
      } catch (IOException e) {
        throw new UncheckedIOException("Not JSON: " + body(), e);
      }
    }
  }
}
