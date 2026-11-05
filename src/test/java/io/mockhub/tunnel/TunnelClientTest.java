package io.mockhub.tunnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TunnelClientTest {

  @Test
  void readsEveryOptionAndTrimsTrailingSlashes() {
    // Given
    String[] args = {"--server", "https://mock.example.com/", "--token", "mh_abc", "--name", "laptop", "--to", "http://localhost:3000/", "--workers", "6"};

    // When
    TunnelClient.Settings settings = TunnelClient.parse(args, Map.of());

    // Then
    assertThat(settings).isEqualTo(new TunnelClient.Settings("https://mock.example.com", "mh_abc", "laptop", "http://localhost:3000", 6));
  }

  @Test
  void takesTheServerAndTokenFromTheEnvironment() {
    // Given
    Map<String, String> env = Map.of("MOCKHUB_URL", "http://localhost:8080", "MOCKHUB_TOKEN", "mh_env");

    // When
    TunnelClient.Settings settings = TunnelClient.parse(new String[] {"--name", "desk", "--to", "http://localhost:4000"}, env);

    // Then
    assertThat(settings.server()).isEqualTo("http://localhost:8080");
    assertThat(settings.token()).isEqualTo("mh_env");
    assertThat(settings.workers()).isEqualTo(4);
  }

  @Test
  void explainsWhatIsMissingOrWrong() {
    // Given
    Map<String, String> env = Map.of("MOCKHUB_URL", "http://localhost:8080", "MOCKHUB_TOKEN", "mh_env");

    // When / Then
    assertThatThrownBy(() -> TunnelClient.parse(new String[] {"--name", "desk", "--to", "http://x"}, Map.of()))
        .hasMessageContaining("--server");
    assertThatThrownBy(() -> TunnelClient.parse(new String[] {"--to", "http://x"}, env)).hasMessageContaining("--name");
    assertThatThrownBy(() -> TunnelClient.parse(new String[] {"--name", "desk", "--to", "localhost:3000"}, env)).hasMessageContaining("--to");
    assertThatThrownBy(() -> TunnelClient.parse(new String[] {"--name", "desk", "--to", "http://x", "--workers", "0"}, env))
        .hasMessageContaining("--workers");
    assertThatThrownBy(() -> TunnelClient.parse(new String[] {"--colour", "red"}, env)).hasMessageContaining("Unknown option --colour");
    assertThatThrownBy(() -> TunnelClient.parse(new String[] {"--name"}, env)).hasMessageContaining("Missing a value");
  }

  @Test
  void usageShowsTheCommand() {
    // Given / When
    String usage = TunnelClient.usage();

    // Then
    assertThat(usage).contains("java -jar mockhub.jar tunnel").contains("tunnel://<name>/path");
  }
}
