package io.mockhub.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.mockhub.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataGeneratorTest {

  private final DataGenerator generator = new DataGenerator(new FakeData("en"));

  @Test
  void generatesRecordsWithTypedAndNestedFields() {
    // When
    ArrayNode records = generator.generate(List.of(
        new DataGenerator.Field("id", DataGenerator.SEQUENCE),
        new DataGenerator.Field("ref", DataGenerator.UUID_TYPE),
        new DataGenerator.Field("name", "Name.fullName"),
        new DataGenerator.Field("age", "Number.numberBetween '18','90'"),
        new DataGenerator.Field("active", "Bool.bool"),
        new DataGenerator.Field("address.city", "Address.city")), 3, null);

    // Then
    assertThat(records).hasSize(3);
    JsonNode first = records.get(0);
    assertThat(first.get("id").asInt()).isEqualTo(1);
    assertThat(records.get(2).get("id").asInt()).isEqualTo(3);
    assertThat(first.get("ref").asText()).hasSize(36);
    assertThat(first.get("name").asText()).isNotBlank();
    assertThat(first.get("age").isNumber()).isTrue();
    assertThat(first.get("age").asInt()).isBetween(18, 90);
    assertThat(first.get("active").isBoolean()).isTrue();
    assertThat(first.at("/address/city").asText()).isNotBlank();
  }

  @Test
  void rejectsBadInput() {
    // When / Then
    assertThatThrownBy(() -> generator.generate(List.of(), 3, null)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> generator.generate(List.of(new DataGenerator.Field("id", "sequence")), 0, null)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> generator.generate(List.of(new DataGenerator.Field("bad name", "sequence")), 1, null)).hasMessageContaining("valid field name");
    assertThatThrownBy(() -> generator.generate(List.of(new DataGenerator.Field("x", "Nope.nothing")), 1, null)).hasMessageContaining("Can't generate");
  }
}
