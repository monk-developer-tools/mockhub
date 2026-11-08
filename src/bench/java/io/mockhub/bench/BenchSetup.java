package io.mockhub.bench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

final class BenchSetup {
  private static final ObjectMapper JSON = new ObjectMapper();

  private final String base;
  private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
  private String token;

  BenchSetup(String base) {
    this.base = base;
  }

  Duration awaitHealthy(Duration timeout) throws InterruptedException {
    Instant start = Instant.now();
    Instant deadline = start.plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      try {
        HttpResponse<Void> r = http.send(HttpRequest.newBuilder(URI.create(base + "/__admin/health")).timeout(Duration.ofSeconds(5)).build(),
            HttpResponse.BodyHandlers.discarding());
        if (r.statusCode() == 200) return Duration.between(start, Instant.now());
      } catch (IOException notListeningYet) {
        Thread.sleep(50);
        continue;
      }
      Thread.sleep(50);
    }
    throw new IllegalStateException("MockHub at " + base + " did not answer /__admin/health within " + timeout + "; it may have stopped");
  }

  void signIn(String username, String password) throws IOException, InterruptedException {
    JsonNode body = JSON.createObjectNode().put("username", username).put("password", password);
    token = call("POST", "/auth/login", body).path("token").asText(null);
    if (token == null) throw new IllegalStateException("Signing in as " + username + " returned no token");
  }

  String createProject(String slug, JsonNode mappings) throws IOException, InterruptedException {
    deleteProject(slug);
    String orgId = call("GET", "/orgs", null).get(0).get("id").asText();
    JsonNode teams = call("GET", "/orgs/" + orgId + "/teams", null);
    var request = JSON.createObjectNode().put("name", "Benchmark " + slug).put("slug", slug).put("orgId", orgId);
    if (teams.isArray() && !teams.isEmpty()) request.put("teamId", teams.get(0).get("id").asText());
    String id = call("POST", "/projects", request).get("id").asText();
    JsonNode imported = call("POST", "/projects/" + id + "/import/wiremock", mappings);
    int expected = mappings.get("mappings").size();
    if (imported.path("imported").asInt() != expected) {
      throw new IllegalStateException("Expected " + expected + " stubs to be imported, got " + imported);
    }
    return id;
  }

  void deleteProject(String slug) throws IOException, InterruptedException {
    for (JsonNode p : call("GET", "/projects", null)) {
      if (slug.equals(p.path("slug").asText())) call("DELETE", "/projects/" + p.get("id").asText(), null);
    }
  }

  private JsonNode call(String method, String path, JsonNode body) throws IOException, InterruptedException {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + "/__admin/mockhub/api" + path)).timeout(Duration.ofSeconds(60))
        .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
        .header("Content-Type", "application/json");
    if (token != null) b.header("Authorization", "Bearer " + token);
    HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    if (r.statusCode() / 100 != 2) throw new IllegalStateException(method + " " + path + " returned " + r.statusCode() + ": " + r.body());
    return r.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(r.body());
  }
}
