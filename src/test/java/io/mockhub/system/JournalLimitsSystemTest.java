package io.mockhub.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

class JournalLimitsSystemTest {
  private static final String AUTH = "Basic " + Base64.getEncoder().encodeToString("lead:s3cret".getBytes(StandardCharsets.UTF_8));
  private static final ObjectMapper JSON = new ObjectMapper();
  private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();

  @TempDir
  Path data;

  private HttpResponse<String> send(MockHubProcess hub, String method, String path, String body, boolean signedIn) throws Exception {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(hub.baseUrl() + path))
        .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    if (signedIn) b.header("Authorization", AUTH);
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void theJournalKeepsOnlyTheNewestRequestsUpToItsLimit() throws Exception {
    // Given
    try (MockHubProcess hub = MockHubProcess.start(data, Map.of("MOCKHUB_USERS", "lead:s3cret:admin", "MOCKHUB_JOURNAL_MAX_ENTRIES", "3"))) {
      ObjectNode mapping = JSON.createObjectNode();
      mapping.putObject("request").put("method", "GET").put("urlPathPattern", "/n/.*");
      mapping.putObject("response").put("status", 200).put("body", "ok");
      assertThat(send(hub, "POST", "/__admin/mappings", mapping.toString(), true).statusCode()).isEqualTo(201);

      // When
      for (int i = 1; i <= 5; i++) send(hub, "GET", "/n/" + i, null, false);
      JsonNode journal = JSON.readTree(send(hub, "GET", "/__admin/requests", null, true).body());

      // Then
      assertThat(journal.get("requests")).extracting(r -> r.path("request").path("url").asText()).containsExactly("/n/5", "/n/4", "/n/3");
    }
  }

  @Test
  void theJournalKeepsOnlyTheStartOfLargeResponseBodiesSoTheyCantFillTheHeap() throws Exception {
    // Given
    String big = "x".repeat(20 * 1024);
    try (MockHubProcess hub = MockHubProcess.start(data, Map.of("MOCKHUB_USERS", "lead:s3cret:admin", "MOCKHUB_JOURNAL_MAX_BODY_KB", "8"))) {
      ObjectNode mapping = JSON.createObjectNode();
      mapping.putObject("request").put("method", "GET").put("url", "/big");
      mapping.putObject("response").put("status", 200).put("body", big);
      assertThat(send(hub, "POST", "/__admin/mappings", mapping.toString(), true).statusCode()).isEqualTo(201);

      // When
      HttpResponse<String> served = send(hub, "GET", "/big", null, false);
      JsonNode journal = JSON.readTree(send(hub, "GET", "/__admin/requests", null, true).body());

      // Then
      assertThat(served.body()).hasSize(big.length());
      String logged = journal.get("requests").get(0).path("response").path("body").asText();
      assertThat(logged.length()).isPositive().isLessThanOrEqualTo(8 * 1024);
    }
  }
}
