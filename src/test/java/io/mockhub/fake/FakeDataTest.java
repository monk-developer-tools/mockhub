package io.mockhub.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FakeDataTest {

  private final FakeData fake = new FakeData("en");

  @Test
  void generatesValuesFromShortAndFullExpressions() {
    // Then
    assertThat(fake.generate("Name.fullName", null)).contains(" ");
    assertThat(fake.generate("Internet.emailAddress", null)).contains("@");
    assertThat(fake.generate("#{Address.city}, #{Address.country}", null)).contains(", ");
    assertThat(Integer.parseInt(fake.generate("Number.numberBetween '10','20'", null))).isBetween(10, 19);
  }

  @Test
  void usesTheRequestedLocale() {
    // When
    String german = fake.generate("Address.country", "de");

    // Then
    assertThat(german).isNotBlank();
    assertThat(new FakeData("fr").generate("Name.firstName", null)).isNotBlank();
  }

  @Test
  void refusesEmptyExpressions() {
    // Then
    assertThatThrownBy(() -> fake.generate(" ", null)).hasMessageContaining("Say what to generate");
  }
}
