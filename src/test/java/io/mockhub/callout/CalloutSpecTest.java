package io.mockhub.callout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalloutSpecTest {

  @Test
  void readsTheWireMockWebhookShape() {
    // Given
    Map<String, Object> parameters = Map.of(
        "method", "PUT",
        "url", "https://partner.test/hooks",
        "headers", Map.of("Content-Type", "application/json", "Accept", List.of("a", "b")),
        "body", "{\"ok\":true}",
        "delay", Map.of("type", "fixed", "milliseconds", 250));

    // When
    CalloutSpec spec = CalloutSpec.from(parameters);

    // Then
    assertThat(spec.method()).isEqualTo("PUT");
    assertThat(spec.url()).isEqualTo("https://partner.test/hooks");
    assertThat(spec.headers()).containsEntry("Content-Type", "application/json").containsEntry("Accept", "a, b");
    assertThat(spec.body()).isEqualTo("{\"ok\":true}");
    assertThat(spec.delayMs()).isEqualTo(250);
    assertThat(spec.forward()).isFalse();
  }

  @Test
  void fillsSafeDefaultsForMissingValues() {
    // Given
    Map<String, Object> parameters = Map.of("url", " /hooks ", "forward", true, "delay", -5);

    // When
    CalloutSpec spec = CalloutSpec.from(parameters);

    // Then
    assertThat(spec.method()).isEqualTo("POST");
    assertThat(spec.url()).isEqualTo("/hooks");
    assertThat(spec.headers()).isEmpty();
    assertThat(spec.body()).isEmpty();
    assertThat(spec.delayMs()).isZero();
    assertThat(spec.forward()).isTrue();
  }
}
