package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mockhub.bench.Benchmark;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkIntegrationTest {

  @TempDir
  Path data;

  @TempDir
  Path out;

  @Test
  void everyBenchmarkScenarioIsAnsweredByItsOwnStubWithoutErrors() throws Exception {
    // Given
    Path results = out.resolve("results.json");
    try (MockHubServer server = MockHubServer.start(data, Map.of("MOCKHUB_USERS", "bench:bench-pass-1:admin"))) {
      String[] args = {"--base", server.baseUrl(), "--user", "bench", "--password", "bench-pass-1", "--connections", "2",
          "--warmup", "0", "--duration", "1", "--only", "hello-c1,payload-10kb,template,post-json,many-stubs", "--out", results.toString()};

      // When
      Benchmark.main(args);
    }

    // Then
    JsonNode report = new ObjectMapper().readTree(results.toFile());
    assertThat(report.get("results")).hasSize(5);
    for (JsonNode r : report.get("results")) {
      assertThat(r.get("errors").asLong()).as(r.get("scenario").asText() + " " + r.path("firstError").asText()).isZero();
      assertThat(r.get("requests").asLong()).as(r.get("scenario").asText()).isPositive();
      assertThat(r.path("latency").path("p99Micros").asDouble()).isPositive();
    }
    assertThat(report.get("results").get(1).get("megabytesPerSecond").asDouble()).isPositive();
  }
}
