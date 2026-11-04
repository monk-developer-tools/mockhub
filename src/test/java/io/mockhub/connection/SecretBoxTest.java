package io.mockhub.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecretBoxTest {

  @TempDir
  Path dir;

  @Test
  void sealsSecretsSoOnlyTheSameKeyOpensThem() {
    // Given
    SecretBox box = SecretBox.of("correct horse battery staple", dir.resolve("unused.key"));

    // When
    String first = box.seal("s3cret");
    String second = box.seal("s3cret");

    // Then
    assertThat(first).startsWith("v1:").doesNotContain("s3cret").isNotEqualTo(second);
    assertThat(box.open(first)).isEqualTo("s3cret");
    assertThat(box.seal("")).isEmpty();
    assertThatThrownBy(() -> SecretBox.of("another key", dir.resolve("unused.key")).open(first))
        .isInstanceOf(SecretBox.SecretException.class).hasMessageContaining("enter it again");
    assertThat(Files.exists(dir.resolve("unused.key"))).isFalse();
  }

  @Test
  void createsAPrivateKeyFileOnceAndReusesIt() throws Exception {
    // Given
    Path keyFile = dir.resolve("mockhub/secret.key");
    String sealed = SecretBox.of("", keyFile).seal("token");

    // When
    SecretBox reopened = SecretBox.of(null, keyFile);

    // Then
    assertThat(reopened.open(sealed)).isEqualTo("token");
    assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(keyFile))).isEqualTo("rw-------");
  }
}
