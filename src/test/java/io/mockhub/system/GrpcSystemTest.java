package io.mockhub.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mockhub.support.GrpcTestClient;
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

class GrpcSystemTest {

  private static final String API = "/__admin/mockhub/api";
  private static final String AUTH = "Basic " + Base64.getEncoder().encodeToString("lead:s3cret".getBytes(StandardCharsets.UTF_8));
  private static final byte[] LIBRARY = GrpcTestClient.resource("library.dsc");
  private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();

  @TempDir
  Path data;

  @Test
  void thePackagedJarServesGrpcFromAnUploadedDescriptorAcrossARestart() throws Exception {
    // Given
    try (MockHubProcess hub = MockHubProcess.start(data, Map.of("MOCKHUB_USERS", "lead:s3cret:admin"))) {
      String projectId = new ObjectMapper().readTree(send(hub, API + "/projects", HttpRequest.BodyPublishers.ofString("{\"name\":\"Library\",\"slug\":\"library\"}")).body())
          .get("id").asText();
      HttpResponse<String> uploaded = send(hub, API + "/projects/" + projectId + "/grpc/descriptors?name=library.dsc", HttpRequest.BodyPublishers.ofByteArray(LIBRARY));
      send(hub, API + "/projects/" + projectId + "/stubs", HttpRequest.BodyPublishers.ofString(
          "{\"request\":{\"method\":\"POST\",\"urlPath\":\"/library.v1.BookService/GetBook\"},"
              + "\"response\":{\"status\":200,\"jsonBody\":{\"id\":\"1\",\"title\":\"Middlemarch\"}},\"metadata\":{\"mockhub\":{\"type\":\"grpc\"}}}"));
      assertThat(uploaded.statusCode()).as(uploaded.body()).isEqualTo(201);
    }

    // When
    GrpcTestClient.Reply reply;
    try (MockHubProcess restarted = MockHubProcess.start(data, Map.of("MOCKHUB_USERS", "lead:s3cret:admin"));
        GrpcTestClient client = new GrpcTestClient(restarted.port(), LIBRARY)) {
      reply = client.unary("library.v1.BookService", "GetBook", "{\"id\":\"1\"}");
    }

    // Then
    assertThat(reply.status()).as(reply.description()).isEqualTo("OK");
    assertThat(reply.json()).contains("Middlemarch");
  }

  private HttpResponse<String> send(MockHubProcess hub, String path, HttpRequest.BodyPublisher body) throws Exception {
    return http.send(HttpRequest.newBuilder(URI.create(hub.baseUrl() + path)).timeout(Duration.ofSeconds(60))
        .header("Authorization", AUTH).POST(body).build(), HttpResponse.BodyHandlers.ofString());
  }
}
