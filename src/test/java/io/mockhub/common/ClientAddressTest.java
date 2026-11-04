package io.mockhub.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClientAddressTest {

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "203.0.113.9 | 203.0.113.9",
      "1.2.3.4, 203.0.113.9 | 203.0.113.9",
      "spoofed, 10.0.0.1 ,203.0.113.9 | 203.0.113.9",
      "2001:db8::1 | 2001:db8::1",
  })
  void trustsOnlyTheAddressAddedByTheNearestProxy(String reported, String expected) {
    // When
    String address = ClientAddress.nearest(reported);

    // Then
    assertThat(address).isEqualTo(expected);
  }
}
