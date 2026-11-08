package io.mockhub.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mockhub.bench.Benchmark;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkSystemTest {

  @TempDir
  Path data;

  @TempDir
  Path out;

  @Test
  void theLoadGeneratorMeasuresThePackagedJarAndRecordsAThroughputTimeline() throws Exception {
    // Given
    Path results = out.resolve("results.json");
    try (MockHubProcess hub = MockHubProcess.start(data, Map.of("MOCKHUB_USERS", "bench:bench-pass-1:admin"))) {
      String[] args = {"--base", hub.baseUrl(), "--user", "bench", "--password", "bench-pass-1", "--connections", "8",
          "--warmup", "1", "--duration", "2", "--soak", "2", "--only", "hello-c16,payload-1mb,steady", "--out", results.toString()};

      // When
      Benchmark.main(args);
    }

    // Then
    JsonNode report = new ObjectMapper().readTree(results.toFile());
    assertThat(report.get("waitedForHealthMillis").asLong()).isGreaterThanOrEqualTo(0);
    assertThat(report.get("results")).extracting(r -> r.get("scenario").asText()).containsExactly("hello-c16", "payload-1mb", "steady");
    JsonNode hello = report.get("results").get(0);
    assertThat(hello.get("connections").asInt()).isEqualTo(16);
    assertThat(hello.get("errors").asLong()).isZero();
    assertThat(hello.get("requestsPerSecond").asDouble()).isGreaterThan(50);
    assertThat(report.get("results").get(1).get("megabytesPerSecond").asDouble()).isGreaterThan(1);
    assertThat(report.get("results").get(2).get("timeline")).isNotEmpty();
  }
}
