package io.mockhub.system;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TunnelSystemTest {

  private static final String API = "/__admin/mockhub/api";
  private static final String AUTH = "Basic " + Base64.getEncoder().encodeToString("lead:s3cret".getBytes(StandardCharsets.UTF_8));
  private static final ObjectMapper JSON = new ObjectMapper();
  private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();

  @TempDir
  Path data;

  private HttpResponse<String> call(String method, String url, String body) throws Exception {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).header("Authorization", AUTH)
        .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    if (body != null) b.header("Content-Type", "application/json");
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void theJarsTunnelCommandCarriesAProxyStubToALocalService() throws Exception {
    // Given
    WireMockServer local = new WireMockServer(options().dynamicPort());
    local.start();
    local.stubFor(get(urlEqualTo("/api/ping")).willReturn(aResponse().withStatus(200).withBody("pong from laptop")));
    Path log = data.resolveSibling(data.getFileName() + "-tunnel.log");
    Process tunnel = null;
    try (MockHubProcess hub = MockHubProcess.start(data, Map.of("MOCKHUB_USERS", "lead:s3cret:admin"))) {
      String projectId = JSON.readTree(call("POST", hub.baseUrl() + API + "/projects", "{\"name\":\"Pay\",\"slug\":\"pay\"}").body()).get("id").asText();
      call("POST", hub.baseUrl() + API + "/projects/" + projectId + "/stubs",
          "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/ping\"},\"response\":{\"proxyBaseUrl\":\"tunnel://laptop/api\"}}");
      String token = JSON.readTree(call("POST", hub.baseUrl() + API + "/auth/tokens", "{\"name\":\"laptop\",\"role\":\"editor\",\"expiresInDays\":7}").body())
          .get("secret").asText();
      ProcessBuilder builder = new ProcessBuilder(List.of(ProcessHandle.current().info().command().orElse("java"), "-jar", MockHubProcess.jarPath(),
          "tunnel", "--server", hub.baseUrl(), "--name", "laptop", "--to", "http://localhost:" + local.port(), "--workers", "2"))
          .redirectErrorStream(true).redirectOutput(log.toFile());
      builder.environment().put("MOCKHUB_TOKEN", token);
      tunnel = builder.start();
      awaitOnline(hub.baseUrl());

      // When
      HttpResponse<String> answer = http.send(HttpRequest.newBuilder(URI.create(hub.baseUrl() + "/pay/ping")).build(), HttpResponse.BodyHandlers.ofString());

      // Then
      assertThat(answer.statusCode()).isEqualTo(200);
      assertThat(answer.body()).isEqualTo("pong from laptop");
      assertThat(Files.readString(log)).contains("Tunnel laptop: tunnel://laptop -> http://localhost:" + local.port());
    } finally {
      if (tunnel != null) {
        tunnel.destroy();
        tunnel.waitFor(30, TimeUnit.SECONDS);
      }
      local.stop();
    }
  }

  @Test
  void theTunnelCommandExplainsMissingOptions() throws Exception {
    // Given
    ProcessBuilder builder = new ProcessBuilder(List.of(ProcessHandle.current().info().command().orElse("java"), "-jar", MockHubProcess.jarPath(), "tunnel"))
        .redirectErrorStream(true);
    builder.environment().remove("MOCKHUB_URL");
    builder.environment().remove("MOCKHUB_TOKEN");

    // When
    Process process = builder.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    boolean ended = process.waitFor(60, TimeUnit.SECONDS);

    // Then
    assertThat(ended).isTrue();
    assertThat(process.exitValue()).isEqualTo(2);
    assertThat(output).contains("--server").contains("Usage: java -jar mockhub.jar tunnel");
  }

  private void awaitOnline(String baseUrl) throws Exception {
    Instant deadline = Instant.now().plusSeconds(30);
    while (Instant.now().isBefore(deadline)) {
      JsonNode tunnels = JSON.readTree(call("GET", baseUrl + API + "/tunnels", null).body());
      if (tunnels.size() == 1 && tunnels.get(0).get("online").asBoolean()) return;
      Thread.sleep(200);
    }
    throw new AssertionError("The tunnel didn't connect");
  }
}
