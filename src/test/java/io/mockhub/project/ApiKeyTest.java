package io.mockhub.project;

import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.ApiException;
import org.junit.jupiter.api.Test;

class ApiKeyTest {

  @Test
  void parsesAHeaderAndValue() {
    // When
    ApiKey key = ApiKey.parse(tree("{'header':'X-Token','value':' abc '}"));

    // Then
    assertThat(key).isEqualTo(new ApiKey("X-Token", "abc"));
  }

  @Test
  void defaultsTheHeaderName() {
    // When
    ApiKey key = ApiKey.parse(tree("{'value':'abc'}"));

    // Then
    assertThat(key.header()).isEqualTo(ApiKey.DEFAULT_HEADER);
  }

  @Test
  void anEmptyValueTurnsTheKeyOff() {
    // When / Then
    assertThat(ApiKey.parse(tree("{'header':'X-Token','value':''}"))).isNull();
    assertThat(ApiKey.parse(tree("null"))).isNull();
    assertThat(ApiKey.parse(null)).isNull();
  }

  @Test
  void rejectsInvalidHeaderNames() {
    // When / Then
    assertThatThrownBy(() -> ApiKey.parse(tree("{'header':'X Token','value':'abc'}"))).isInstanceOf(ApiException.class);
  }

  @Test
  void acceptsOnlyTheExactValue() {
    // Given
    ApiKey key = new ApiKey("X-API-Key", "secret");

    // When / Then
    assertThat(key.accepts("secret")).isTrue();
    assertThat(key.accepts("Secret")).isFalse();
    assertThat(key.accepts(null)).isFalse();
  }
}
