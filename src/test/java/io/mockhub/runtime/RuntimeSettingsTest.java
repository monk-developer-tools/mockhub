package io.mockhub.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.support.Fixtures;
import org.junit.jupiter.api.Test;

class RuntimeSettingsTest {

  @Test
  void parsesEverySettingAndDropsEmptyOnes() {
    // Given
    String json = "{\"defaultResponse\":{\"status\":\"503\",\"headers\":{\" X-A \":\"1\",\"\":\"x\"}},"
        + "\"rateLimit\":{\"requests\":10,\"per\":\"Second\"},\"latency\":{\"mode\":\"none\"},"
        + "\"cors\":{\"origins\":[\"https://a.example/\"],\"methods\":[\"get\"]},\"ipAllowList\":[\" 10.0.0.0/8 \",\"\"]}";

    // When
    RuntimeSettings settings = RuntimeSettings.parse(Fixtures.json(json));

    // Then
    assertThat(settings.defaultResponse().status()).isEqualTo(503);
    assertThat(settings.defaultResponse().headers()).containsOnlyKeys("X-A");
    assertThat(settings.rateLimit()).isEqualTo(new RateLimit(10, "second"));
    assertThat(settings.latency()).isNull();
    assertThat(settings.cors().origins()).containsExactly("https://a.example");
    assertThat(settings.cors().methods()).containsExactly("GET");
    assertThat(settings.ipAllowList()).containsExactly("10.0.0.0/8");
  }

  @Test
  void buildsALowestPriorityStubScopedToItsProject() {
    // Given
    DefaultResponse response = new DefaultResponse(404, java.util.Map.of("X-Why", "default"), "{}", true);

    // When
    ObjectNode stub = DefaultResponseStubs.stub(Fixtures.project("pay"), response);

    // Then
    assertThat(stub.get("priority").asInt()).isEqualTo(DefaultResponseStubs.PRIORITY);
    assertThat(stub.at("/request/customMatcher/name").asText()).isEqualTo(ProjectScopeMatcher.NAME);
    assertThat(stub.at("/request/customMatcher/parameters/projectId").asText()).isEqualTo("p-pay");
    assertThat(stub.at("/response/transformers/0").asText()).isEqualTo("response-template");
    assertThat(stub.at("/metadata/mockhub/managed").asText()).isEqualTo("default-response");
  }
}
