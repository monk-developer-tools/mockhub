package io.mockhub.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.ApiException;
import io.mockhub.project.Project;
import io.mockhub.runtime.IpAllowList;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProxyServiceTest {

  private static final Project CAPTURED = new Project("p1", "Captured", "captured", "", "", "", "", "o", "t", null, false, null);

  @TempDir
  Path dir;

  private ProxyService service(boolean enabled) {
    return new ProxyService(enabled, IpAllowList.ofText("127.0.0.0/8, 10.0.0.0/8"), dir.resolve("ca-keystore.jks"),
        id -> id.equals("p1") ? Optional.of(CAPTURED) : Optional.empty());
  }

  @Test
  void remembersTheChosenProjectAcrossRestarts() {
    // Given
    service(true).choose("p1");

    // When
    ProxyService restarted = service(true);

    // Then
    assertThat(restarted.project()).contains(CAPTURED);
    assertThat(restarted.view().at("/project/basePath").asText()).isEqualTo("/captured");
  }

  @Test
  void forgetsADeletedProjectAndRefusesUnknownOnes() {
    // Given
    ProxyService proxy = service(true);
    proxy.choose("p1");

    // When
    proxy.onProjectDeleted(CAPTURED);

    // Then
    assertThat(proxy.project()).isEmpty();
    assertThatThrownBy(() -> proxy.choose("missing")).isInstanceOf(ApiException.class);
  }

  @Test
  void allowsOnlyTheConfiguredNetworks() {
    // Given
    ProxyService proxy = service(true);

    // When / Then
    assertThat(proxy.allows("127.0.0.1")).isTrue();
    assertThat(proxy.allows("10.1.2.3")).isTrue();
    assertThat(proxy.allows("203.0.113.9")).isFalse();
    assertThatThrownBy(() -> IpAllowList.ofText("10.0.0.0/8, nonsense")).hasMessageContaining("nonsense");
  }

  @Test
  void cannotBeConfiguredWhenOff() {
    // When / Then
    assertThatThrownBy(() -> service(false).choose("p1")).hasMessageContaining("MOCKHUB_PROXY=true");
    assertThat(service(false).caCertificate()).isEmpty();
  }
}
