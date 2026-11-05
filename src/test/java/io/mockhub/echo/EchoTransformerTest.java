package io.mockhub.echo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EchoTransformerTest {

  @ParameterizedTest
  @CsvSource({
      "/echo/orders/7, /echo, /orders/7",
      "/echo, /echo, /",
      "/echo/, /echo, /",
      "/echoes/1, /echo, /echoes/1",
      "/orders, '', /orders",
  })
  void givesThePathInsideTheProject(String path, String basePath, String expected) {
    // When
    String relative = EchoTransformer.relativePath(path, basePath);

    // Then
    assertThat(relative).isEqualTo(expected);
  }
}
