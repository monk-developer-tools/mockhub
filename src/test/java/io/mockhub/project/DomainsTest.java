package io.mockhub.project;

import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DomainsTest {

  @ParameterizedTest
  @CsvSource({
      "Payments.Mocks.Example.com, payments.mocks.example.com",
      "api.test., api.test",
      "localhost, localhost",
      "' x-1.example.io ', x-1.example.io",
  })
  void normalizesHostNames(String input, String expected) {
    // When
    String host = Domains.normalize(input);

    // Then
    assertThat(host).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://a.example.com", "a.example.com:8080", "a.example.com/x", "*.example.com", "10.0.0.1", "intranet", "-a.example.com", "a..example.com"})
  void refusesWhatIsNotAHostName(String input) {
    // When / Then
    assertThatThrownBy(() -> Domains.normalize(input)).isInstanceOf(ApiException.class);
  }

  @Test
  void parsesAListWithoutRepeatsAndLimitsItToTen() {
    // Given
    String eleven = "[" + String.join(",", java.util.stream.IntStream.range(0, 11).mapToObj(i -> "'h" + i + ".example.com'").toList()) + "]";

    // When
    var parsed = Domains.parse(tree("['A.example.com', 'a.example.com', '', 'b.example.com']"));

    // Then
    assertThat(parsed).containsExactly("a.example.com", "b.example.com");
    assertThatThrownBy(() -> Domains.parse(tree(eleven))).hasMessageContaining("at most 10");
  }

  @ParameterizedTest
  @CsvSource({
      "Payments.Example.com:8080, payments.example.com",
      "payments.example.com., payments.example.com",
      "payments.example.com, payments.example.com",
  })
  void readsTheHostFromAHostHeader(String header, String expected) {
    // When / Then
    assertThat(Domains.hostOf(header)).contains(expected);
  }

  @Test
  void ignoresMissingAndIpv6HostHeaders() {
    // When / Then
    assertThat(Domains.hostOf(null)).isEmpty();
    assertThat(Domains.hostOf("[::1]:8080")).isEmpty();
  }
}
