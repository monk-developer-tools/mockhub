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

final class ApiClient {

  private static final ObjectMapper JSON = new ObjectMapper();
  private final HttpClient http = HttpClient.newHttpClient();
  private final String baseUrl;
  private String authorization;

  ApiClient(String baseUrl) {
    this.baseUrl = baseUrl;
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
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
        .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    if (contentType != null) b.header("Content-Type", contentType);
    if (authorization != null && path.startsWith("/__admin")) b.header("Authorization", authorization);
    try {
      HttpResponse<byte[]> r = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
      return new Result(r.statusCode(), r.body(), r.headers().firstValue("Content-Type").orElse(""),
          r.headers().firstValue("Content-Disposition").orElse(""));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  record Result(int status, byte[] bytes, String contentType, String contentDisposition) {
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
