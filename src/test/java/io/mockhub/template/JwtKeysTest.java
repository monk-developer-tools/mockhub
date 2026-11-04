package io.mockhub.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JwtKeysTest {

  @TempDir
  Path data;

  @Test
  void theSigningKeyIsCreatedOnceAndKeptAcrossRestarts() {
    // Given
    JwtKeys first = new JwtKeys(data);
    String kid = first.kid();

    // When
    JwtKeys afterRestart = new JwtKeys(data);

    // Then
    assertThat(afterRestart.kid()).isEqualTo(kid);
    assertThat(afterRestart.publicKey()).isEqualTo(first.publicKey());
    @SuppressWarnings("unchecked")
    Map<String, Object> jwk = ((List<Map<String, Object>>) afterRestart.jwks().get("keys")).get(0);
    assertThat(jwk).containsEntry("kty", "RSA").containsEntry("alg", "RS256").containsEntry("kid", kid).containsKeys("n", "e");
    assertThat(jwk).doesNotContainKeys("d", "p", "q");
  }
}
