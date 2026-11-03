package io.mockhub.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.ApiException;
import io.mockhub.support.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class IpAllowListTest {

  @Test
  void allowsAddressesInsideTheListedRanges() {
    // Given
    IpAllowList list = new IpAllowList(List.of("10.0.0.0/8", "192.168.1.7", "2001:db8::/32"));

    // When
    boolean inside = list.allows("10.20.30.40");
    boolean exact = list.allows("192.168.1.7");
    boolean v6 = list.allows("2001:db8:1::5");
    boolean mapped = list.allows("::ffff:10.1.1.1");
    boolean outside = list.allows("11.0.0.1");

    // Then
    assertThat(inside).isTrue();
    assertThat(exact).isTrue();
    assertThat(v6).isTrue();
    assertThat(mapped).isTrue();
    assertThat(outside).isFalse();
  }

  @Test
  void anEmptyListAllowsEveryone() {
    // Given
    IpAllowList list = new IpAllowList(List.of());

    // When
    boolean allowed = list.allows("8.8.8.8");

    // Then
    assertThat(allowed).isTrue();
  }

  @Test
  void rejectsHostNamesAndImpossibleRangesWithoutLookingThemUp() {
    // Given
    String[] bad = {"example.com", "999.1.1.1", "10.0.0.0/33", "fe80::/129", "10.0.0"};

    // When / Then
    for (String entry : bad) {
      assertThatThrownBy(() -> RuntimeSettings.parse(Fixtures.json("{\"ipAllowList\":[\"" + entry + "\"]}")).ipRules())
          .isInstanceOf(ApiException.class)
          .hasMessageContaining(entry);
    }
  }
}
