package io.mockhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigTest {

  @Test
  void usesSensibleDefaults() {
    // When
    Config config = new Config(Map.of());

    // Then
    assertThat(config.port).isEqualTo(8080);
    assertThat(config.dataDir.toString()).endsWith("mockhub-data");
    assertThat(config.users).isEmpty();
    assertThat(config.stubCors).isTrue();
    assertThat(config.journalMaxEntries).isEqualTo(5000);
  }

  @Test
  void readsEnvironment() {
    // Given
    Map<String, String> env = Map.of(
        "PORT", "9000",
        "MOCKHUB_PUBLIC_URL", "https://mocks.acme.com/",
        "MOCKHUB_USERS", "alice:pw:admin, bob:pw2:viewer,carol:pw3",
        "WIREMOCK_OPTIONS", "--verbose  --async-response-enabled true");

    // When
    Config config = new Config(env);

    // Then
    assertThat(config.port).isEqualTo(9000);
    assertThat(config.publicUrl).isEqualTo("https://mocks.acme.com");
    assertThat(config.users).extracting(Config.AuthUser::role).containsExactly("admin", "viewer", "admin");
    assertThat(config.extraWireMockArgs).containsExactly("--verbose", "--async-response-enabled", "true");
  }

  @Test
  void rejectsBadUserSpecs() {
    assertThatThrownBy(() -> new Config(Map.of("MOCKHUB_USERS", "alice"))).hasMessageContaining("user:password");
    assertThatThrownBy(() -> new Config(Map.of("MOCKHUB_USERS", "alice:pw:root"))).hasMessageContaining("Unknown role");
  }
}
