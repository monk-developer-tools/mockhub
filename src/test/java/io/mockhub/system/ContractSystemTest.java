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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContractSystemTest {

  private static final String API = "/__admin/mockhub/api";
  private static final String AUTH = "Basic " + Base64.getEncoder().encodeToString("lead:s3cret".getBytes(StandardCharsets.UTF_8));
  private static final ObjectMapper JSON = new ObjectMapper();
  private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();

  @TempDir
  Path data;

  private HttpResponse<String> call(String method, String url, String body) throws Exception {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
        .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    if (url.contains("/__admin")) b.header("Authorization", AUTH);
    if (body != null) b.header("Content-Type", "application/json");
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void thePackagedJarChecksTrafficAgainstAContractAndKeepsItAcrossRestarts() throws Exception {
    // Given
    String spec = "openapi: 3.0.3\ninfo: { title: Pets, version: '1' }\npaths:\n  /pets/{id}:\n    get:\n      responses:\n        '200':\n"
        + "          description: ok\n          content:\n            application/json:\n              schema:\n                type: object\n"
        + "                required: [name]\n                properties: { name: { type: string } }\n";
    String projectId;
    try (MockHubProcess hub = MockHubProcess.start(data, Map.of("MOCKHUB_USERS", "lead:s3cret:admin"))) {
      projectId = JSON.readTree(call("POST", hub.baseUrl() + API + "/projects", "{\"name\":\"Pets\",\"slug\":\"pets\"}").body()).get("id").asText();
      assertThat(call("PUT", hub.baseUrl() + API + "/projects/" + projectId + "/contract",
          JSON.createObjectNode().put("content", spec).put("source", "upload").toString()).statusCode()).isEqualTo(200);
      call("POST", hub.baseUrl() + API + "/projects/" + projectId + "/stubs",
          "{\"request\":{\"method\":\"GET\",\"urlPathPattern\":\"/pets/.*\"},\"response\":{\"status\":200,\"headers\":{\"Content-Type\":\"application/json\"},\"jsonBody\":{\"name\":7}}}");
      call("GET", hub.baseUrl() + "/pets/pets/1", null);

      // When
      JsonNode drift = JSON.readTree(call("POST", hub.baseUrl() + API + "/projects/" + projectId + "/drift/check", "").body());

      // Then
      assertThat(drift.at("/findings/0/kind").asText()).isEqualTo("RESPONSE_BODY_SCHEMA");
      assertThat(drift.at("/findings/0/detail").asText()).isEqualTo("200 $.name: integer found, string expected");
    }
    try (MockHubProcess restarted = MockHubProcess.start(data, Map.of("MOCKHUB_USERS", "lead:s3cret:admin"))) {
      JsonNode contract = JSON.readTree(call("GET", restarted.baseUrl() + API + "/projects/" + projectId + "/contract", null).body());
      JsonNode drift = JSON.readTree(call("GET", restarted.baseUrl() + API + "/projects/" + projectId + "/drift", null).body());
      assertThat(contract.get("title").asText()).isEqualTo("Pets");
      assertThat(drift.get("open").asInt()).isEqualTo(1);
    }
  }
}
