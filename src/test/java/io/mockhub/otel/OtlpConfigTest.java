package io.mockhub.otel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OtlpConfigTest {

  @Test
  void readsTheStandardOpenTelemetryVariables() {
    // Given
    Map<String, String> env = Map.of(
        "OTEL_EXPORTER_OTLP_ENDPOINT", "http://collector:4318/",
        "OTEL_EXPORTER_OTLP_LOGS_ENDPOINT", "https://logs.example.com/ingest",
        "OTEL_METRICS_EXPORTER", "none",
        "OTEL_EXPORTER_OTLP_HEADERS", "x-api-key=abc%3D1,team=mocks",
        "OTEL_RESOURCE_ATTRIBUTES", "deployment.environment=staging",
        "OTEL_SERVICE_NAME", "mockhub-staging",
        "OTEL_BSP_SCHEDULE_DELAY", "250");

    // When
    OtlpConfig config = OtlpConfig.from(env);

    // Then
    assertThat(config.enabled()).isTrue();
    assertThat(config.tracesUrl()).isEqualTo("http://collector:4318/v1/traces");
    assertThat(config.logsUrl()).isEqualTo("https://logs.example.com/ingest");
    assertThat(config.metricsUrl()).isEmpty();
    assertThat(config.headers()).containsEntry("x-api-key", "abc=1").containsEntry("team", "mocks");
    assertThat(config.resource()).containsEntry("service.name", "mockhub-staging").containsEntry("deployment.environment", "staging");
    assertThat(config.batchDelayMs()).isEqualTo(250);
    assertThat(config.metricIntervalMs()).isEqualTo(60000);
    assertThat(config.protocol()).isEqualTo("http/json");
  }

  @Test
  void isOffWithoutAnEndpointAndNamesTheServiceMockhub() {
    // Given
    // When
    OtlpConfig config = OtlpConfig.from(Map.of("OTEL_BSP_SCHEDULE_DELAY", "soon"));

    // Then
    assertThat(config.enabled()).isFalse();
    assertThat(config.resource()).containsEntry("service.name", "mockhub");
    assertThat(config.batchDelayMs()).isEqualTo(5000);
  }
}
