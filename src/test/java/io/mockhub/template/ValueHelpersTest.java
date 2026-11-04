package io.mockhub.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValueHelpersTest {

  @Test
  void convertsTextToWholeOrDecimalNumbersAndFallsBackOtherwise() {
    // When / Then
    assertThat(ValueHelpers.number("42", null)).isEqualTo(42L);
    assertThat(ValueHelpers.number(" 19.50 ", null)).isEqualTo(19.5);
    assertThat(ValueHelpers.number("1e3", null)).isEqualTo(1000L);
    assertThat(ValueHelpers.number("abc", "none")).isEqualTo("none");
    assertThat(ValueHelpers.number(null, 0)).isEqualTo(0);
  }

  @Test
  void measuresTextListsAndObjects() {
    // When / Then
    assertThat(ValueHelpers.length("Grace")).isEqualTo(5);
    assertThat(ValueHelpers.length(List.of(1, 2, 3))).isEqualTo(3);
    assertThat(ValueHelpers.length(Map.of("a", 1))).isEqualTo(1);
    assertThat(ValueHelpers.length(new int[] {1, 2})).isEqualTo(2);
    assertThat(ValueHelpers.length(null)).isZero();
  }
}
