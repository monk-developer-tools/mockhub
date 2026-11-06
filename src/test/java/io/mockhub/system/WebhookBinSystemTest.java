package io.mockhub.system;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebhookBinSystemTest {

  private static final Map<String, String> ON = Map.of("MOCKHUB_PUBLIC_TOOLS", "true");
  private static final ObjectMapper JSON = new ObjectMapper();
  private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();

  @TempDir
  Path data;

  private HttpResponse<String> call(String method, String url, String body, String token) throws Exception {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
        .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    if (body != null) b.header("Content-Type", "application/json");
    if (token != null) b.header("X-Bin-Token", token);
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void aBinReachesALocalServiceThroughItsOwnTunnelAndKeepsItsRequestsAcrossARestart() throws Exception {
    // Given
    WireMockServer local = new WireMockServer(options().dynamicPort());
    local.start();
    local.stubFor(post(urlEqualTo("/hooks/stripe")).willReturn(aResponse().withStatus(201).withBody("handled on laptop")));
    Process tunnel = null;
    String id;
    String token;
    try (MockHubProcess hub = MockHubProcess.start(data, ON)) {
      JsonNode bin = JSON.readTree(call("POST", hub.baseUrl() + "/__public/bins", null, null).body());
      id = bin.get("id").asText();
      token = bin.get("token").asText();
      call("PUT", hub.baseUrl() + "/__public/inspect/" + id + "/routing", "{\"mode\":\"tunnel\",\"pathPrefix\":\"/hooks\"}", token);
      tunnel = new ProcessBuilder(List.of(ProcessHandle.current().info().command().orElse("java"), "-jar", MockHubProcess.jarPath(),
          "tunnel", "--server", hub.baseUrl(), "--token", "bin_" + id + "_" + token, "--name", "bin-" + id, "--to", "http://localhost:" + local.port()))
          .redirectErrorStream(true).redirectOutput(data.resolveSibling(data.getFileName() + "-bin-tunnel.log").toFile()).start();
      awaitOnline(hub.baseUrl(), id, token);

      // When
      HttpResponse<String> answer = call("POST", hub.baseUrl() + "/__public/bins/" + id + "/stripe", "{\"type\":\"charge.succeeded\"}", null);

      // Then
      assertThat(answer.statusCode()).isEqualTo(201);
      assertThat(answer.body()).isEqualTo("handled on laptop");
    } finally {
      if (tunnel != null) {
        tunnel.destroy();
        tunnel.waitFor(30, TimeUnit.SECONDS);
      }
      local.stop();
    }

    try (MockHubProcess again = MockHubProcess.start(data, ON)) {
      // When
      JsonNode listing = JSON.readTree(call("GET", again.baseUrl() + "/__public/inspect/" + id, null, token).body());

      // Then
      assertThat(listing.get("total").asLong()).isEqualTo(1);
      assertThat(listing.at("/requests/0/event").asText()).isEqualTo("charge.succeeded");
      assertThat(listing.at("/requests/0/outcome/by").asText()).isEqualTo("tunnel");
      assertThat(listing.at("/requests/0/outcome/body").asText()).isEqualTo("handled on laptop");
    }
  }

  private void awaitOnline(String baseUrl, String id, String token) throws Exception {
    Instant deadline = Instant.now().plusSeconds(30);
    while (Instant.now().isBefore(deadline)) {
      JsonNode settings = JSON.readTree(call("GET", baseUrl + "/__public/inspect/" + id + "/settings", null, token).body());
      if (settings.at("/tunnel/online").asBoolean()) return;
      Thread.sleep(200);
    }
    throw new AssertionError("The bin's tunnel didn't connect");
  }
}
