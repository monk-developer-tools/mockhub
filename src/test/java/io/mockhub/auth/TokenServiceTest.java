package io.mockhub.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.support.MutableClock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TokenServiceTest {

  private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
  private final TokenService tokens = new TokenService("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), clock, Duration.ofHours(1));

  @Test
  void issuedTokensVerifyUntilTheyExpire() {
    // Given
    String token = tokens.issue("user-1");

    // When / Then
    assertThat(tokens.verify(token)).hasValueSatisfying(c -> {
      assertThat(c.subject()).isEqualTo("user-1");
      assertThat(c.issuedAt()).isEqualTo(clock.millis());
    });
    clock.advance(Duration.ofMinutes(61));
    assertThat(tokens.verify(token)).isEmpty();
  }

  @Test
  void rejectsTamperedAndForeignTokens() {
    // Given
    String token = tokens.issue("user-1");
    TokenService other = new TokenService("another-secret-another-secret-xx".getBytes(StandardCharsets.UTF_8), clock, Duration.ofHours(1));

    // Then
    assertThat(tokens.verify(token.substring(0, token.length() - 2) + "xx")).isEmpty();
    assertThat(tokens.verify(other.issue("user-1"))).isEmpty();
    assertThat(tokens.verify("not-a-token")).isEmpty();
    assertThat(tokens.verify("a.b.c")).isEmpty();
    assertThat(tokens.verify(null)).isEmpty();
  }

  @Test
  void keepsTheSecretAcrossRestarts(@TempDir Path dir) throws Exception {
    // Given
    TokenService first = TokenService.fromDataDir(dir, clock);
    String token = first.issue("user-1");

    // When
    TokenService afterRestart = TokenService.fromDataDir(dir, clock);

    // Then
    assertThat(afterRestart.verify(token)).isPresent();
    assertThat(Files.readString(dir.resolve("mockhub/secret.key"))).isNotBlank();
  }
}
