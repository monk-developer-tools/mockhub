package io.mockhub.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitSyncSystemTest {

  private static final String API = "/__admin/mockhub/api";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String BASIC = "Basic " + Base64.getEncoder().encodeToString("lead:s3cret".getBytes(StandardCharsets.UTF_8));
  private static final Map<String, String> ENV = Map.of("MOCKHUB_USERS", "lead:s3cret:admin", "MOCKHUB_GIT_ALLOW_PRIVATE", "true");

  @TempDir
  Path first;
  @TempDir
  Path second;
  @TempDir
  Path repos;
  private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();

  @Test
  void aSecondMockHubStartsFromTheRepositoryTheFirstPushedTo() throws Exception {
    // Given
    Path bare = repos.resolve("mocks.git");
    Git.init().setBare(true).setDirectory(bare.toFile()).setInitialBranch("main").call().close();
    String link = "{\"url\":\"" + bare.toUri() + "\",\"folder\":\"mocks/orders\"}";
    JsonNode pushed;
    try (MockHubProcess hub = MockHubProcess.start(first, ENV)) {
      String id = json(send(hub, "POST", API + "/projects", "{\"name\":\"Orders\",\"slug\":\"orders\"}")).get("id").asText();
      send(hub, "POST", API + "/projects/" + id + "/stubs",
          "{\"name\":\"Get order\",\"request\":{\"method\":\"GET\",\"urlPath\":\"/orders/1\"},\"response\":{\"status\":200,\"body\":\"SHIPPED\"}}");
      send(hub, "PUT", API + "/projects/" + id + "/git", link);
      pushed = json(send(hub, "POST", API + "/projects/" + id + "/git/push", "{\"message\":\"Orders mocks\"}"));
    }

    // When
    HttpResponse<String> mock;
    JsonNode pulled;
    try (MockHubProcess hub = MockHubProcess.start(second, ENV)) {
      String id = json(send(hub, "POST", API + "/projects", "{\"name\":\"Orders\",\"slug\":\"orders\"}")).get("id").asText();
      send(hub, "PUT", API + "/projects/" + id + "/git", link);
      pulled = json(send(hub, "POST", API + "/projects/" + id + "/git/pull", "{}"));
      mock = http.send(HttpRequest.newBuilder(URI.create(hub.baseUrl() + "/orders/orders/1")).build(), HttpResponse.BodyHandlers.ofString());
    }

    // Then
    assertThat(pushed.get("changed").asBoolean()).as(pushed.toString()).isTrue();
    assertThat(pulled.get("commit").asText()).isEqualTo(pushed.get("commit").asText());
    assertThat(mock.statusCode()).isEqualTo(200);
    assertThat(mock.body()).isEqualTo("SHIPPED");
  }

  @Test
  void thePackagedJarRendersTemplatesForSignedInPeople() throws Exception {
    // Given
    try (MockHubProcess hub = MockHubProcess.start(first, ENV)) {
      // When
      JsonNode rendered = json(send(hub, "POST", API + "/templates/render",
          "{\"template\":\"Hello {{request.query.name}}\",\"request\":{\"method\":\"GET\",\"url\":\"/?name=Ada\"}}"));

      // Then
      assertThat(rendered.get("output").asText()).isEqualTo("Hello Ada");
    }
  }

  private HttpResponse<String> send(MockHubProcess hub, String method, String path, String body) throws Exception {
    HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(hub.baseUrl() + path))
        .header("Authorization", BASIC).header("Content-Type", "application/json")
        .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(method + " " + path + ": " + response.body()).isLessThan(300);
    return response;
  }

  private static JsonNode json(HttpResponse<String> response) throws Exception {
    return JSON.readTree(response.body());
  }
}
