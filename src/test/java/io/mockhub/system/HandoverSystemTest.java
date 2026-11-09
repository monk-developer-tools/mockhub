package io.mockhub.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HandoverSystemTest {

  private static final String API = "/__admin/mockhub/api";
  private static final String BASIC = "Basic " + Base64.getEncoder().encodeToString("lead:s3cret".getBytes(StandardCharsets.UTF_8));
  private static final Map<String, String> HA = Map.of("MOCKHUB_USERS", "lead:s3cret:admin", "MOCKHUB_HA", "true", "MOCKHUB_HA_WAIT_SECONDS", "60");

  @TempDir
  Path data;
  private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build();

  private int status(String url) {
    try {
      return http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2)).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    } catch (Exception e) {
      return -1;
    }
  }

  @Test
  void aNewServerTakesOverFromTheRunningOneWithOnlyAShortPause() throws Exception {
    // Given
    try (MockHubProcess first = MockHubProcess.start(data, HA)) {
      http.send(HttpRequest.newBuilder(URI.create(first.baseUrl() + API + "/projects")).header("Authorization", BASIC).header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Orders\",\"slug\":\"orders\"}")).build(), HttpResponse.BodyHandlers.ofString());
      String projects = http.send(HttpRequest.newBuilder(URI.create(first.baseUrl() + API + "/projects")).header("Authorization", BASIC).build(),
          HttpResponse.BodyHandlers.ofString()).body();
      String id = projects.replaceAll("(?s).*\"id\" : \"([^\"]+)\".*", "$1");
      http.send(HttpRequest.newBuilder(URI.create(first.baseUrl() + API + "/projects/" + id + "/stubs")).header("Authorization", BASIC)
          .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(
              "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/status\"},\"response\":{\"status\":200,\"body\":\"UP\"}}")).build(),
          HttpResponse.BodyHandlers.ofString());
      assertThat(status(first.baseUrl() + "/orders/status")).isEqualTo(200);
      AtomicLong lastFromFirst = new AtomicLong(System.currentTimeMillis());
      CompletableFuture<Void> watching = CompletableFuture.runAsync(() -> {
        long until = System.currentTimeMillis() + 60_000;
        int misses = 0;
        while (System.currentTimeMillis() < until && misses < 3) {
          if (status(first.baseUrl() + "/orders/status") == 200) {
            lastFromFirst.set(System.currentTimeMillis());
            misses = 0;
          } else {
            misses++;
          }
        }
      });

      // When
      try (MockHubProcess second = MockHubProcess.start(data, HA)) {
        long firstFromSecond = System.currentTimeMillis();
        boolean firstStopped = first.exitsWithin(Duration.ofSeconds(15));
        watching.join();
        HttpResponse<String> answer = http.send(HttpRequest.newBuilder(URI.create(second.baseUrl() + "/orders/status")).build(),
            HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(firstStopped).isTrue();
        assertThat(first.exitCode()).isZero();
        assertThat(answer.body()).isEqualTo("UP");
        assertThat(first.stdout()).contains("A new MockHub asked to take over");
        assertThat(second.stdout()).contains("Took over after");
        long pause = firstFromSecond - lastFromFirst.get();
        System.out.println("Handover pause: " + pause + " ms; second took over after it asked: " + second.stdout().replaceAll("(?s).*Took over after (\\d+) ms.*", "$1") + " ms; " + first.stdout().replaceAll("(?s).*(MockHub stopped in[^\\n]*).*", "$1"));
        assertThat(pause).isLessThan(5_000);
      }
    }
  }
}
