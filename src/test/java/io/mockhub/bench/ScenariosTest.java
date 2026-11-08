package io.mockhub.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ScenariosTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void payloadsAreValidJsonOfExactlyTheAdvertisedSize() throws Exception {
    // Given
    int[] sizes = Scenarios.SIZES;

    // When
    List<String> bodies = java.util.Arrays.stream(sizes).mapToObj(Scenarios::payload).toList();

    // Then
    for (int i = 0; i < sizes.length; i++) {
      assertThat(bodies.get(i).getBytes(StandardCharsets.UTF_8)).hasSize(sizes[i]);
      assertThat(JSON.readTree(bodies.get(i)).path("count").asInt()).isPositive();
    }
    assertThat(Scenarios.label(1024)).isEqualTo("1kb");
    assertThat(Scenarios.label(1024 * 1024)).isEqualTo("1mb");
  }

  @Test
  void theMappingsCoverEveryScenarioWithProjectRelativePaths() {
    // Given
    Scenarios scenarios = new Scenarios("mockhub:8080", 32);

    // When
    JsonNode mappings = Scenarios.mappings().get("mappings");
    List<Scenario> all = scenarios.all();

    // Then
    assertThat(mappings.size()).isEqualTo(1 + Scenarios.SIZES.length + 2);
    assertThat(Scenarios.manyStubs().get("mappings")).hasSize(Scenarios.MANY_STUBS);
    assertThat(mappings.get(0).path("request").path("urlPath").asText()).isEqualTo("/hello");
    assertThat(all).extracting(Scenario::name).containsExactly("hello-c1", "hello-c16", "hello-c64", "hello-c256",
        "payload-1kb", "payload-10kb", "payload-100kb", "payload-1mb", "template", "post-json", "many-stubs");
    String request = new String(all.get(8).source().next(new SplittableRandom(1)), StandardCharsets.US_ASCII);
    assertThat(request).startsWith("GET /bench/users/42 HTTP/1.1\r\nHost: mockhub:8080\r\n");
    String order = new String(all.get(9).source().next(new SplittableRandom(1)), StandardCharsets.UTF_8);
    String item = new String(all.get(10).source().next(new SplittableRandom(1)), StandardCharsets.US_ASCII);
    assertThat(item).startsWith("GET /bench-many/items/");
    assertThat(order).startsWith("POST /bench/orders HTTP/1.1").contains("Content-Length: 36\r\n").endsWith("{\"sku\":\"A-1\",\"qty\":2,\"note\":\"bench\"}");
  }

  @Test
  void readsCommandLineOptionsAndRejectsAMissingValue() {
    // Given
    String[] args = {"--base", "http://mockhub:8080", "--warmup", "5", "--duration", "30", "--soak", "600", "--only", "hello-c1,template"};

    // When
    Benchmark.Options options = Benchmark.Options.parse(args);

    // Then
    assertThat(options.base()).isEqualTo("http://mockhub:8080");
    assertThat(options.warmup()).isEqualTo(Duration.ofSeconds(5));
    assertThat(options.measure()).isEqualTo(Duration.ofSeconds(30));
    assertThat(options.soak()).isEqualTo(Duration.ofMinutes(10));
    assertThat(options.connections()).isEqualTo(32);
    assertThat(options.only()).containsExactly("hello-c1", "template");
    assertThatThrownBy(() -> Benchmark.Options.parse(new String[] {"--base"})).hasMessageContaining("needs a value");
  }
}
