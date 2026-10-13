package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.HealthCheck;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HealthCheckIntegrationTest {

  @TempDir
  Path data;

  @Test
  void reportsARunningServerAsHealthyAndAStoppedOneAsNot() {
    // Given
    String url;

    // When / Then
    try (MockHubServer server = MockHubServer.start(data)) {
      url = server.baseUrl();
      assertThat(HealthCheck.isHealthy(url)).isTrue();
    }
    assertThat(HealthCheck.isHealthy(url)).isFalse();
  }
}
