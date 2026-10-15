package io.mockhub.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

  private final PasswordHasher hasher = new PasswordHasher(1000);

  @Test
  void hashesWithASaltAndVerifies() {
    // When
    String first = hasher.hash("correct horse");
    String second = hasher.hash("correct horse");

    // Then
    assertThat(first).startsWith("pbkdf2:1000:").isNotEqualTo(second);
    assertThat(hasher.matches(first, "correct horse")).isTrue();
    assertThat(hasher.matches(first, "wrong horse")).isFalse();
  }

  @Test
  void acceptsTheLegacyFormatsFromMockhubUsers() {
    // Then
    assertThat(hasher.matches("secret", "secret")).isTrue();
    assertThat(hasher.matches("sha256:2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b", "secret")).isTrue();
    assertThat(hasher.matches("sha256:2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b", "nope")).isFalse();
    assertThat(hasher.matches(null, "x")).isFalse();
    assertThat(hasher.matches("pbkdf2:broken", "x")).isFalse();
  }
}
